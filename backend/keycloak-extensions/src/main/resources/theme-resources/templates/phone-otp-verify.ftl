<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('otp') displayInfo=true; section>
    <#if section = "header">
        ${msg("phoneOtpVerifyTitle", "Enter Verification Code")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <div class="otp-info-box">
                    <p class="otp-sent-text">
                        ${msg("otpSentTo", "A verification code has been sent to")}
                    </p>
                    <p class="phone-display">
                        <strong>${phone!""}</strong>
                    </p>
                </div>

                <form id="kc-otp-verify-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <label for="otp" class="${properties.kcLabelClass!}">
                            ${msg("verificationCode", "Verification Code")}
                        </label>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input type="text"
                                   id="otp"
                                   name="otp"
                                   class="${properties.kcInputClass!} otp-input"
                                   inputmode="numeric"
                                   pattern="[0-9]*"
                                   maxlength="6"
                                   autocomplete="one-time-code"
                                   placeholder="000000"
                                   autofocus
                                   required />
                        </div>
                        <#if messagesPerField.existsError('otp')>
                            <span id="input-error-otp" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('otp'))?no_esc}
                            </span>
                        </#if>
                    </div>

                    <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                               type="submit"
                               value="${msg("verifyCode", "Verify Code")}" />
                    </div>
                </form>

                <div class="otp-actions">
                    <p class="otp-timer" id="otpTimer">
                        ${msg("codeExpiresIn", "Code expires in")} <span id="countdown">${expiresIn!300}</span> ${msg("seconds", "seconds")}
                    </p>
                    <p class="resend-link">
                        ${msg("didntReceiveCode", "Didn't receive the code?")}
                        <a href="${url.loginAction}?resend=true" id="resendLink" class="disabled">
                            ${msg("resendCode", "Resend code")}
                        </a>
                    </p>
                </div>
            </div>
        </div>
    <#elseif section = "info">
        <div id="kc-info" class="${properties.kcSignUpClass!}">
            <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                <p class="${properties.kcInfoClass!}">
                    <a href="${url.loginUrl}">${msg("useAnotherPhone", "Use a different phone number")}</a>
                </p>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>

<style>
    .otp-info-box {
        background: #f8f9fa;
        border-radius: 8px;
        padding: 1rem;
        margin-bottom: 1.5rem;
        text-align: center;
    }

    .otp-sent-text {
        margin: 0 0 0.5rem 0;
        color: #666;
        font-size: 0.9rem;
    }

    .phone-display {
        margin: 0;
        font-size: 1.1rem;
        color: #333;
    }

    .otp-input {
        font-size: 1.5rem !important;
        letter-spacing: 0.5rem;
        text-align: center;
        font-family: monospace;
        padding: 0.75rem !important;
    }

    .otp-input::placeholder {
        letter-spacing: 0.3rem;
        color: #ccc;
    }

    .otp-actions {
        margin-top: 1.5rem;
        text-align: center;
    }

    .otp-timer {
        color: #666;
        font-size: 0.9rem;
        margin-bottom: 0.5rem;
    }

    .otp-timer #countdown {
        font-weight: bold;
        color: #333;
    }

    .resend-link {
        font-size: 0.9rem;
        color: #666;
    }

    .resend-link a {
        color: #0066cc;
        text-decoration: none;
        margin-left: 0.25rem;
    }

    .resend-link a:hover {
        text-decoration: underline;
    }

    .resend-link a.disabled {
        color: #999;
        pointer-events: none;
        cursor: not-allowed;
    }
</style>

<script>
    (function() {
        var countdownEl = document.getElementById('countdown');
        var resendLink = document.getElementById('resendLink');
        var seconds = parseInt(countdownEl.textContent, 10) || 300;
        var resendDelay = 60; // Enable resend after 60 seconds

        function updateCountdown() {
            if (seconds > 0) {
                seconds--;
                countdownEl.textContent = seconds;

                // Enable resend link after delay
                if (seconds <= (parseInt('${expiresIn!300}', 10) - resendDelay)) {
                    resendLink.classList.remove('disabled');
                }

                setTimeout(updateCountdown, 1000);
            } else {
                countdownEl.parentElement.innerHTML = '${msg("codeExpired", "Code has expired. Please request a new one.")}';
                resendLink.classList.remove('disabled');
            }
        }

        // Start countdown
        setTimeout(updateCountdown, 1000);

        // Auto-format OTP input
        var otpInput = document.getElementById('otp');
        otpInput.addEventListener('input', function(e) {
            this.value = this.value.replace(/[^0-9]/g, '').slice(0, 6);
        });

        // Auto-submit when 6 digits entered
        otpInput.addEventListener('input', function(e) {
            if (this.value.length === 6) {
                // Small delay before auto-submit for UX
                setTimeout(function() {
                    document.getElementById('kc-otp-verify-form').submit();
                }, 300);
            }
        });
    })();
</script>
