package com.pml.identity.service.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * OWASP-Compliant File Upload Validator
 *
 * Implements defense-in-depth file upload validation following OWASP guidelines:
 * - Whitelist-based file type validation using magic numbers
 * - File size enforcement
 * - Filename sanitization to prevent path traversal
 * - Extension validation
 * - Content-type verification
 *
 * References:
 * - OWASP File Upload Cheat Sheet
 * - OWASP Top 10 - A03:2021 Injection
 */
@Slf4j
@Component
public class FileUploadValidator {

    // ========================================================================
    // CONFIGURATION - Externalize to application.yml in production
    // ========================================================================

    /**
     * Maximum file size: 10MB
     * Prevents denial-of-service via large file uploads
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Maximum filename length: 255 characters
     * Prevents buffer overflow and filesystem issues
     */
    private static final int MAX_FILENAME_LENGTH = 255;

    /**
     * Allowed file extensions (whitelist approach)
     * Only document and image types commonly used for verification
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "webp"
    );

    /**
     * Allowed MIME types (whitelist approach)
     * Must match allowed extensions
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    /**
     * File magic numbers (signatures) for validation
     * First 8 bytes of common file types to prevent content-type spoofing
     */
    private static final List<MagicNumber> MAGIC_NUMBERS = Arrays.asList(
            new MagicNumber("PDF", new byte[]{0x25, 0x50, 0x44, 0x46}, "application/pdf"),
            new MagicNumber("JPEG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg"),
            new MagicNumber("PNG", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, "image/png"),
            new MagicNumber("WEBP", new byte[]{0x52, 0x49, 0x46, 0x46}, "image/webp") // RIFF header
    );

    /**
     * Regex for safe filenames
     * Allows alphanumeric, underscores, hyphens, dots (but not at start)
     * Prevents path traversal (../, ..\, etc.)
     */
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_\\-\\.]*$");

    // ========================================================================
    // PUBLIC VALIDATION API
    // ========================================================================

    /**
     * Validates a file upload against OWASP security guidelines
     *
     * @param filePart Spring WebFlux FilePart
     * @return Mono<ValidationResult> with validation outcome
     */
    public Mono<ValidationResult> validate(FilePart filePart) {
        log.debug("Validating file upload: {}", filePart.filename());

        // 1. Validate filename (sanitization and length)
        String filename = filePart.filename();
        ValidationResult filenameResult = validateFilename(filename);
        if (!filenameResult.isValid()) {
            return Mono.just(filenameResult);
        }

        // 2. Validate file extension (whitelist)
        ValidationResult extensionResult = validateExtension(filename);
        if (!extensionResult.isValid()) {
            return Mono.just(extensionResult);
        }

        // 3. Validate MIME type (from headers)
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "";
        ValidationResult mimeResult = validateMimeType(contentType);
        if (!mimeResult.isValid()) {
            return Mono.just(mimeResult);
        }

        // 4. Validate file size and magic number (requires reading content)
        return validateFileContent(filePart);
    }

    /**
     * Validates raw file data (for direct buffer validation)
     *
     * @param filename     Original filename
     * @param contentType  MIME type from header
     * @param fileSize     File size in bytes
     * @param firstBytes   First 16 bytes of file content
     * @return ValidationResult
     */
    public ValidationResult validateRawFile(
            String filename,
            String contentType,
            long fileSize,
            byte[] firstBytes) {

        // Filename validation
        ValidationResult filenameResult = validateFilename(filename);
        if (!filenameResult.isValid()) {
            return filenameResult;
        }

        // Extension validation
        ValidationResult extensionResult = validateExtension(filename);
        if (!extensionResult.isValid()) {
            return extensionResult;
        }

        // MIME type validation
        ValidationResult mimeResult = validateMimeType(contentType);
        if (!mimeResult.isValid()) {
            return mimeResult;
        }

        // File size validation
        if (fileSize > MAX_FILE_SIZE) {
            return ValidationResult.invalid(
                    "File size exceeds maximum allowed (" + (MAX_FILE_SIZE / 1024 / 1024) + "MB)"
            );
        }

        if (fileSize == 0) {
            return ValidationResult.invalid("File is empty");
        }

        // Magic number validation
        ValidationResult magicResult = validateMagicNumber(firstBytes, contentType);
        if (!magicResult.isValid()) {
            return magicResult;
        }

        return ValidationResult.valid();
    }

    // ========================================================================
    // VALIDATION HELPERS
    // ========================================================================

    /**
     * Validates filename against path traversal and length limits
     * Following OWASP recommendation to use basename() equivalent
     */
    private ValidationResult validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return ValidationResult.invalid("Filename is required");
        }

        // Extract basename to prevent path traversal
        String basename = Paths.get(filename).getFileName().toString();

        // Check length
        if (basename.length() > MAX_FILENAME_LENGTH) {
            return ValidationResult.invalid("Filename too long (max " + MAX_FILENAME_LENGTH + " characters)");
        }

        // Check for path traversal patterns
        if (basename.contains("..") || basename.contains("/") || basename.contains("\\")) {
            return ValidationResult.invalid("Invalid filename: path traversal detected");
        }

        // Validate against safe pattern
        if (!SAFE_FILENAME_PATTERN.matcher(basename).matches()) {
            return ValidationResult.invalid("Invalid filename: contains disallowed characters");
        }

        return ValidationResult.valid();
    }

    /**
     * Validates file extension using whitelist approach
     * OWASP: Never rely on extension alone, but use as first filter
     */
    private ValidationResult validateExtension(String filename) {
        String extension = getFileExtension(filename);

        if (extension.isEmpty()) {
            return ValidationResult.invalid("File extension is required");
        }

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            return ValidationResult.invalid(
                    "File type not allowed. Allowed types: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Validates MIME type using whitelist approach
     */
    private ValidationResult validateMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return ValidationResult.invalid("Content-Type header is required");
        }

        // Handle charset suffix (e.g., "image/jpeg; charset=UTF-8")
        String baseMimeType = mimeType.split(";")[0].trim().toLowerCase();

        if (!ALLOWED_MIME_TYPES.contains(baseMimeType)) {
            return ValidationResult.invalid(
                    "MIME type not allowed: " + baseMimeType
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Validates file content: size and magic number
     * Requires reading file bytes (reactive)
     */
    private Mono<ValidationResult> validateFileContent(FilePart filePart) {
        return filePart.content()
                .reduce(0L, (size, buffer) -> size + buffer.readableByteCount())
                .flatMap(totalSize -> {
                    // Validate size
                    if (totalSize > MAX_FILE_SIZE) {
                        return Mono.just(ValidationResult.invalid(
                                "File size exceeds maximum allowed (" + (MAX_FILE_SIZE / 1024 / 1024) + "MB)"
                        ));
                    }

                    if (totalSize == 0) {
                        return Mono.just(ValidationResult.invalid("File is empty"));
                    }

                    // Validate magic number (requires re-reading first bytes)
                    return validateMagicNumberFromPart(filePart);
                });
    }

    /**
     * Validates magic number (file signature) to prevent content-type spoofing
     */
    private Mono<ValidationResult> validateMagicNumberFromPart(FilePart filePart) {
        return filePart.content()
                .next() // Get first buffer
                .map(buffer -> {
                    byte[] firstBytes = new byte[Math.min(16, buffer.readableByteCount())];
                    buffer.read(firstBytes);

                    String contentType = filePart.headers().getContentType() != null
                            ? filePart.headers().getContentType().toString()
                            : "";

                    return validateMagicNumber(firstBytes, contentType);
                })
                .defaultIfEmpty(ValidationResult.invalid("Unable to read file content"));
    }

    /**
     * Validates magic number against known file signatures
     */
    private ValidationResult validateMagicNumber(byte[] fileBytes, String declaredMimeType) {
        if (fileBytes == null || fileBytes.length < 4) {
            return ValidationResult.invalid("File too small or corrupted");
        }

        // Find matching magic number
        for (MagicNumber magic : MAGIC_NUMBERS) {
            if (matchesMagicNumber(fileBytes, magic.signature)) {
                // Verify declared MIME type matches detected type
                String baseMimeType = declaredMimeType.split(";")[0].trim().toLowerCase();
                if (!magic.mimeType.equalsIgnoreCase(baseMimeType)) {
                    return ValidationResult.invalid(
                            "File content does not match declared type. " +
                                    "Detected: " + magic.type + ", Declared: " + baseMimeType
                    );
                }
                return ValidationResult.valid();
            }
        }

        return ValidationResult.invalid("Unsupported or corrupted file format");
    }

    /**
     * Checks if file bytes match a magic number signature
     */
    private boolean matchesMagicNumber(byte[] fileBytes, byte[] signature) {
        if (fileBytes.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (fileBytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }

    // ========================================================================
    // HELPER CLASSES
    // ========================================================================

    /**
     * Represents a file magic number (signature)
     */
    private record MagicNumber(String type, byte[] signature, String mimeType) {
    }

    /**
     * Validation result with success/failure status and error message
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
