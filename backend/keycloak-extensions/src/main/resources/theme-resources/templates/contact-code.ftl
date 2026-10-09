<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('code') displayInfo=true; section>
    <#if section = "header">
        ${msg("contact.code.title")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <p id="contact-sent">${msg("contact.code.sentTo", (maskedContact!''))}</p>
                <#-- Rendered by the server; no script is needed or used. -->
                <p id="contact-expiry">
                    <#if (expiresInSeconds!0) gt 0>${msg("contact.code.expiresIn", ((expiresInSeconds + 59) / 60)?int?c)}<#else>${msg("contact.code.expired")}</#if>
                </p>
                <form id="kc-contact-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post" novalidate>
                    <div class="${properties.kcFormGroupClass!}">
                        <label for="code" class="${properties.kcLabelClass!}">${msg("contact.code.label")}</label>
                        <input type="text"
                               id="code"
                               name="code"
                               class="${properties.kcInputClass!}"
                               inputmode="numeric"
                               pattern="[0-9 ]*"
                               autocomplete="one-time-code"
                               maxlength="10"
                               aria-describedby="code-error"
                               aria-invalid="${messagesPerField.existsError('code')?c}"
                               autofocus
                               required />
                        <span id="code-error" class="${properties.kcInputErrorMessageClass!}" aria-live="polite" role="status">
                            <#if messagesPerField.existsError('code')>${kcSanitize(messagesPerField.get('code'))?no_esc}</#if>
                        </span>
                    </div>
                    <div class="${properties.kcFormGroupClass!}">
                        <button type="submit" name="action" value="verify" id="kc-code-submit" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}">${msg("contact.code.verify")}</button>
                    </div>
                    <div class="${properties.kcFormGroupClass!}">
                        <#if (resendAfterSeconds!0) gt 0>
                            <p id="contact-resend-wait">${msg("contact.code.resendAfter", resendAfterSeconds?c)}</p>
                        </#if>
                        <button type="submit" name="action" value="resend" id="kc-code-resend" formnovalidate class="${properties.kcButtonClass!} ${properties.kcButtonSecondaryClass!}">${msg("contact.code.resend")}</button>
                        <button type="submit" name="action" value="change" id="kc-code-change" formnovalidate class="${properties.kcButtonClass!} ${properties.kcButtonSecondaryClass!}">${msg("contact.code.change")}</button>
                    </div>
                </form>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
