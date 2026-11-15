# Empty shells for the Stripe test-mode values. Terraform never holds the values: you load them once
# (see infra/README.md) and every session's tasks read them. They persist so they are loaded once, not per session.
resource "aws_secretsmanager_secret" "stripe_secret_key" {
  name        = "storefront/stripe-secret-key"
  description = "Stripe TEST-mode secret key (sk_test_...)"
}

resource "aws_secretsmanager_secret" "stripe_webhook_secret" {
  name        = "storefront/stripe-webhook-secret"
  description = "Signing secret of the Stripe dashboard webhook endpoint for the API hostname (whsec_...), not the one from `stripe listen`"
}
