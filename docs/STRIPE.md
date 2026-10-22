# Stripe and Premium

## Rules this code follows

- The Stripe **secret key exists only on the server** (`apps/api/.env` / the host's environment). The launcher and website never get it. They only call MOTION's API.
- **Premium is granted only from verified webhooks**, or from the server reading Stripe directly ("Restore purchases"). The success page and the launcher's "I paid" flow unlock nothing. They only ask the server to refresh.
- **Webhook signatures are verified.** MOTION checks the `Stripe-Signature` header with HMAC-SHA256 against the raw body, with a 5-minute tolerance and a constant-time compare. Each event is processed once: event ids are recorded, so replays are ignored.
- **Separate test and live modes.** The server refuses a live key outside `BLOOM_ENV=production`, and a test key in production unless `STRIPE_ALLOW_TEST_KEYS=1` is set for staging.
- **Nothing sensitive is logged.** Logs contain event ids, types and MOTION user ids, never card data or keys.

## Setup

1. **Create products.** In the Stripe Dashboard, create a product "MOTION Premium" with up to three prices:
   - monthly (recurring) → `STRIPE_PRICE_MONTHLY`
   - yearly (recurring) → `STRIPE_PRICE_YEARLY`
   - lifetime (one-time) → `STRIPE_PRICE_LIFETIME`

   Leave any of these empty to hide that plan.
2. **Add your API key.** **Developers → API keys**: put the secret key in `STRIPE_SECRET_KEY`. A restricted key with Checkout, Customers, Subscriptions, Billing Portal and Charges access also works.
3. **Configure the webhook.** **Developers → Webhooks → Add endpoint**: `https://YOUR-DOMAIN/v1/stripe/webhook`, using API version `2024-06-20` (the same as `STRIPE_API_VERSION`). Send these events:
   - `checkout.session.completed`
   - `checkout.session.async_payment_succeeded`
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
   - `customer.subscription.paused`
   - `customer.subscription.resumed`
   - `charge.refunded`

   Copy the signing secret into `STRIPE_WEBHOOK_SECRET`.
4. **Enable the Customer Portal.** Do this under **Settings → Billing → Customer portal**. It lets users cancel, change plan and update their card.
5. **Run the server.**
   - With Docker: `docker compose up --build`.
   - Or `npm run build:api && npm start -w @bloom/api`, behind HTTPS, with `PUBLIC_URL` and `TRUST_PROXY=1`.

## Local testing

```bash
stripe listen --forward-to localhost:8787/v1/stripe/webhook   # prints a whsec_… for .env
npm run dev:api
stripe trigger checkout.session.completed
```

Use test card `4242 4242 4242 4242`.

## How a purchase flows

1. **Checkout.** The launcher (signed in through the Minecraft-verified challenge) calls `POST /v1/billing/checkout {plan}`.
2. **The server creates the Checkout Session.** It attaches the MOTION user id as `client_reference_id` and as metadata.
3. **Payment.** The user pays on Stripe's hosted page.
4. **Webhook.** Stripe sends a webhook. The server verifies it, finds the user and stores the subscription or purchase.
5. **Entitlement.** The launcher polls `GET /v1/me` and Premium features unlock from the server's entitlement.
6. **Refunds and cancellations** arrive as webhooks and remove access at the right time. A cancelled subscription stays active until the period ends.

## Manual grants

```bash
npm run grant -w @bloom/api -- <minecraft name|uuid> [days] [reason]
npm run grant -w @bloom/api -- <minecraft name|uuid> revoke
```
