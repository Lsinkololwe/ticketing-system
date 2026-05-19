<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('phoneNumber') displayInfo=true; section>
    <#if section = "header">
        ${msg("phoneOtpTitle", "Sign in with Phone")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <form id="kc-phone-otp-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <label for="phoneNumber" class="${properties.kcLabelClass!}">
                            ${msg("phoneNumber", "Phone Number")}
                        </label>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input type="tel"
                                   id="phoneNumber"
                                   name="phoneNumber"
                                   class="${properties.kcInputClass!}"
                                   placeholder="+260 97X XXX XXX"
                                   autocomplete="tel"
                                   autofocus
                                   required />
                        </div>
                        <#if messagesPerField.existsError('phoneNumber')>
                            <span id="input-error-phoneNumber" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('phoneNumber'))?no_esc}
                            </span>
                        </#if>
                    </div>

                    <div class="${properties.kcFormGroupClass!}">
                        <label for="channel" class="${properties.kcLabelClass!}">
                            ${msg("deliveryChannel", "Receive code via")}
                        </label>
                        <div class="${properties.kcInputWrapperClass!}">
                            <div class="channel-options">
                                <label class="channel-option">
                                    <input type="radio" name="channel" value="whatsapp" checked />
                                    <span class="channel-label">
                                        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                                            <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/>
                                        </svg>
                                        WhatsApp
                                    </span>
                                </label>
                                <label class="channel-option">
                                    <input type="radio" name="channel" value="sms" />
                                    <span class="channel-label">
                                        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                                            <path d="M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
                                        </svg>
                                        SMS
                                    </span>
                                </label>
                            </div>
                        </div>
                    </div>

                    <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                               type="submit"
                               value="${msg("sendVerificationCode", "Send Verification Code")}" />
                    </div>
                </form>
            </div>
        </div>
    <#elseif section = "info">
        <div id="kc-info" class="${properties.kcSignUpClass!}">
            <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                <p class="${properties.kcInfoClass!}">
                    ${msg("phoneOtpInfo", "We'll send a verification code to your phone via WhatsApp or SMS.")}
                </p>
                <p class="${properties.kcInfoClass!}">
                    <a href="${url.loginUrl}">${msg("backToLogin", "Back to login")}</a>
                </p>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>

<style>
    .channel-options {
        display: flex;
        gap: 1rem;
        margin-top: 0.5rem;
    }

    .channel-option {
        flex: 1;
        cursor: pointer;
    }

    .channel-option input[type="radio"] {
        display: none;
    }

    .channel-label {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        padding: 0.75rem 1rem;
        border: 2px solid #e0e0e0;
        border-radius: 8px;
        transition: all 0.2s ease;
        background: #fff;
    }

    .channel-option input[type="radio"]:checked + .channel-label {
        border-color: #25D366;
        background: #f0fff4;
        color: #25D366;
    }

    .channel-option:last-child input[type="radio"]:checked + .channel-label {
        border-color: #2196F3;
        background: #e3f2fd;
        color: #2196F3;
    }

    .channel-label:hover {
        border-color: #999;
    }

    .channel-label svg {
        flex-shrink: 0;
    }
</style>
