<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('contact') displayInfo=true; section>
    <#if section = "header">
        ${msg("contact.title")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <form id="kc-contact-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post" novalidate>
                    <div class="${properties.kcFormGroupClass!}">
                        <label for="contact" class="${properties.kcLabelClass!}">${msg("contact.label")}</label>
                        <input type="text"
                               id="contact"
                               name="contact"
                               class="${properties.kcInputClass!}"
                               value="${(contactValue!'')}"
                               inputmode="email"
                               autocomplete="username"
                               autocapitalize="none"
                               spellcheck="false"
                               maxlength="254"
                               aria-describedby="contact-hint contact-error"
                               aria-invalid="${messagesPerField.existsError('contact')?c}"
                               autofocus
                               required />
                        <span id="contact-hint" class="${properties.kcInputHelperTextClass!}">${msg("contact.hint")}</span>
                        <span id="contact-error" class="${properties.kcInputErrorMessageClass!}" aria-live="polite" role="status">
                            <#if messagesPerField.existsError('contact')>${kcSanitize(messagesPerField.get('contact'))?no_esc}</#if>
                        </span>
                    </div>
                    <div class="${properties.kcFormGroupClass!}">
                        <button type="submit" id="kc-contact-submit" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}">${msg("contact.send")}</button>
                    </div>
                    <p class="${properties.kcInputHelperTextClass!}">${msg("contact.privacy")}</p>
                </form>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
