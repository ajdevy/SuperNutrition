# Stripe for SuperNutrition mobile subscriptions

**Research date:** 2026-07-26
**Scope:** iOS App Store, Google Play, Stripe Checkout, and app-to-web subscription flows.

> This is a platform-policy and product-architecture assessment, not legal advice. Apple and Google are actively changing regional payment programs, so the final submission configuration should be rechecked before release.

## Executive conclusion

Stripe is suitable for SuperNutrition’s website subscriptions and for selected app-to-web flows. It is not a universal replacement for Apple In-App Purchase or Google Play Billing.

If SuperNutrition Premium unlocks digital functionality consumed in the app—such as AI food/photo analysis, premium meal plans, analytics, or app-only content—the safe global default is:

- iOS: StoreKit In-App Purchase.
- Android: Google Play Billing.
- Web: Stripe Checkout and Stripe Billing.

Website purchases can share access with the apps through a common backend entitlement service, but an app must not advertise or link to that external checkout unless the relevant platform and storefront rules allow it.

## Product classification

The payment rule depends on what the customer is buying, not on whether Stripe technically works in the mobile SDK.

| Product or service | iOS | Android | Stripe suitability |
| --- | --- | --- | --- |
| Premium app features, AI analysis, digital meal plans, premium content | StoreKit is required by default | Play Billing is required by default | Use Stripe on the web; use app-store billing in the general mobile case |
| Physical goods or services consumed outside the app | Apple requires a non-IAP payment method | Google says Play Billing must not be used | Stripe or another normal payment processor is appropriate |
| Real-time one-to-one coaching or consultation | Non-IAP payment is permitted | Non-Play payment is permitted when the service is one-to-one and not available for replay | Stripe is appropriate, subject to the service and consumer-law requirements |
| Subscription purchased on the website and consumed in the app | Must still fit Apple’s account, multiplatform, and link-out rules | A consumption-only model is allowed if the app does not sell the product inside the app | Stripe can process the web purchase; backend entitlements must be synchronized |

Apple states that unlocking app functionality or subscriptions requires In-App Purchase, while its exceptions include person-to-person services and goods or services consumed outside the app. [Apple App Review Guidelines, sections 3.1.1 and 3.1.3](https://developer.apple.com/app-store/review/guidelines/)

Google’s Payments policy requires Play Billing for digital subscriptions and app functionality, and explicitly excludes physical goods, physical services, and qualifying one-to-one online services. [Google Play Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en) [Google Play policy guidance](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en)

## iOS findings

### General rule

Apple’s current guideline says that subscriptions and features which unlock functionality inside an app must use In-App Purchase. A plain Stripe SDK, embedded checkout page, or hidden web flow does not override that rule.

### Website redirects and external purchase links

Apple’s current guideline makes the following regional distinction:

- **United States storefront:** buttons, external links, and other calls to action for alternative purchase mechanisms are currently permitted without the StoreKit external-purchase-link entitlement.
- **EU storefronts:** developers can communicate and promote web offers after accepting the applicable Apple terms, receiving the entitlement, and implementing the StoreKit External Purchase APIs and disclosure sheet. Apple’s current EU guidance says that In-App Purchase cannot be offered in the same app, storefront, and platform where these external-purchase promotions are enabled.
- **Russia and other specifically supported storefronts:** the StoreKit external purchase-link entitlement and storefront-specific configuration are required. The allowed-country list includes Russia and EU countries for this entitlement.
- **Other storefronts:** the default prohibition on directing users to a non-Apple purchase method remains in place, except for narrow exceptions such as reader apps and other qualifying categories.

Apple’s EU program also requires transaction reporting and may apply acquisition, store-services, and technology fees. External purchases shift payment support, refunds, taxes, and subscription management responsibility to the developer. [Apple EU external-purchase guidance](https://developer.apple.com/support/communication-and-promotion-of-offers-on-the-app-store-in-the-eu/) [Apple external-purchase API](https://developer.apple.com/documentation/storekit/external_purchase) [Apple external-purchase-link regions](https://developer.apple.com/documentation/bundleresources/information-property-list/skexternalpurchaselink)

### Stripe’s documented iOS flow

Stripe documents an iOS app-to-web flow for digital goods and subscriptions in the US: the app creates a Checkout Session through the backend, opens Stripe Checkout in a browser, and receives the result through a universal link. Stripe also documents subscription management through a hosted Customer Portal. [Stripe mobile digital-goods guide](https://docs.stripe.com/mobile/digital-goods) [Stripe iOS Checkout flow](https://docs.stripe.com/mobile/digital-goods/checkout)

This should be implemented only after storefront eligibility is checked. The backend must provision access from Stripe webhook events rather than trusting a client-side success redirect.

## Android findings

### General rule

Google Play requires Play Billing for digital subscriptions, premium app functionality, and digital content unless an applicable alternative program is used. Google also prohibits buttons, links, webviews, sign-up flows, or other calls to action that lead to an alternate payment method outside those programs.

### Website redirects and alternative billing

- **United States:** Google’s External Content Links program allows a Play-distributed app to link users to a website to purchase in-app digital items. Enrollment, Google’s external-links APIs, a pre-link information screen, customer support, refunds or refund disclosures, and transaction reporting are required. Google’s separate US Alternative Billing program permits Stripe or another alternative billing system inside the app.
- **EEA:** Google’s External Offers program supports links to external offers. The app must enroll and use the External Offers APIs. Under the current program rules, external offers use the developer’s billing service without combining those links with Play Billing or user-choice billing in the same program configuration.
- **Other supported alternative-billing markets:** Google has additional country-specific alternative-billing and user-choice programs, but their eligibility and UX requirements differ. Do not generalize the US or EEA implementation to every country.
- **All other markets:** Play Billing remains the safe default for digital subscriptions.

Google continues to apply service fees and requires reporting even when a qualifying alternative or external payment program is used. Google’s US program is connected to a US District Court order and may change if that order changes. [Google Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en) [US External Content Links](https://support.google.com/googleplay/android-developer/answer/16470497?hl=en-EN) [US Alternative Billing](https://support.google.com/googleplay/android-developer/answer/16497028?hl=en) [EEA External Offers](https://support.google.com/googleplay/android-developer/answer/14372887?hl=en) [External Offers APIs](https://developer.android.com/google/play/billing/external)

## Recommended SuperNutrition architecture

### Phase 1: lowest policy and operational risk

1. Sell Premium on the web with Stripe Checkout and Stripe Billing.
2. Use StoreKit subscriptions in the iOS app.
3. Use Google Play subscriptions in the Android app.
4. Maintain one account-level entitlement service in the backend.
5. Let users manage Stripe subscriptions through Stripe Customer Portal and store subscriptions through Apple or Google’s subscription-management surfaces.

The entitlement record should identify the account, provider, product, state, renewal/expiry time, and provider transaction identifier. Provider webhooks or verified store notifications should update it; the mobile client should only read the resulting entitlement.

### Phase 2: regional app-to-web optimization

Add explicit storefront/region routing only where the business case justifies the additional compliance work:

| Region and platform | Possible Stripe route | Recommendation |
| --- | --- | --- |
| iOS US | Stripe Checkout opened from the app | Viable; implement Apple storefront detection and backend webhooks |
| Android US | External website link through Google’s program, or native Stripe through Alternative Billing | Viable; enroll first and use Google’s required APIs |
| iOS EU | Apple external-purchase entitlement and StoreKit disclosure/reporting | Defer unless EU conversion or margin justifies the operational burden |
| Android EEA | Google External Offers program | Defer unless the fee/reporting trade-off is acceptable |
| Other iOS/Android storefronts | Usually store billing for digital app subscriptions | Keep native store billing |

Do not ship a single unconditional “Pay on our website” button in both apps. The button and checkout path need storefront-aware policy gating.

## Russia-specific constraint

If the merchant entity is based in Russia, Stripe is a separate feasibility problem: Stripe currently says it does not support users located in Russia, Ukraine, and Belarus, and Russian-issued cards are unlikely to succeed. Google Play has also suspended billing for users in Russia and monetization for developers whose payout bank account is in Russia. A website redirect alone does not solve those acquiring, payout, sanctions, or local-payment constraints. [Stripe sanctions and Russia guidance](https://support.stripe.com/questions/sanctions-on-russia-and-belarus) [Google Play Russia billing guidance](https://support.google.com/googleplay/android-developer/answer/11950272?hl=en-GB)

## Decision

For the current SuperNutrition digital-subscription model, implement **StoreKit + Google Play Billing for mobile and Stripe for web**. Add app-to-web Stripe checkout only as a deliberately gated regional optimization, starting with the US after completing the relevant Apple/Google enrollment and review requirements.

## Source register

All policy pages below were checked on 2026-07-26:

- [Apple App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [Apple EU external-purchase guidance](https://developer.apple.com/support/communication-and-promotion-of-offers-on-the-app-store-in-the-eu/)
- [Apple StoreKit external-purchase APIs](https://developer.apple.com/documentation/storekit/external_purchase)
- [Google Play Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en)
- [Google US External Content Links program](https://support.google.com/googleplay/android-developer/answer/16470497?hl=en-EN)
- [Google US Alternative Billing program](https://support.google.com/googleplay/android-developer/answer/16497028?hl=en)
- [Google EEA External Offers program](https://support.google.com/googleplay/android-developer/answer/14372887?hl=en)
- [Stripe mobile digital-goods documentation](https://docs.stripe.com/mobile/digital-goods)
- [Stripe iOS Checkout flow](https://docs.stripe.com/mobile/digital-goods/checkout)
- [Stripe global availability](https://stripe.com/global)
