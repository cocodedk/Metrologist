#!/bin/sh
set -eu

# Signing setup for cocodedk/Metrologist
# Generates or reuses release.keystore and uploads secrets to GitHub.
# DO NOT run this in CI — run once locally, then delete the keystore passphrase from your shell history.

OWNER="cocodedk"
REPO="Metrologist"
KEYSTORE_FILE="release.keystore"
KEY_ALIAS="metrologist-release"

echo "==> Signing setup for ${OWNER}/${REPO} (app: Metrologist)"
echo ""

# ── Keystore ────────────────────────────────────────────────────────────────
if [ -f "$KEYSTORE_FILE" ]; then
  echo "Found existing keystore: $KEYSTORE_FILE"
else
  echo "No keystore found — generating a new one."
  echo "Enter a KEYSTORE_PASSWORD (no double-quotes allowed):"
  stty -echo
  read -r KEYSTORE_PASSWORD
  stty echo
  echo ""
  case "$KEYSTORE_PASSWORD" in
    *'"'*)
      echo "ERROR: Password must not contain a double-quote character."
      exit 1
      ;;
  esac

  echo "Enter a KEY_PASSWORD (no double-quotes allowed, or press Enter to reuse KEYSTORE_PASSWORD):"
  stty -echo
  read -r KEY_PASSWORD
  stty echo
  echo ""
  if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
  fi
  case "$KEY_PASSWORD" in
    *'"'*)
      echo "ERROR: Key password must not contain a double-quote character."
      exit 1
      ;;
  esac

  keytool -genkeypair \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=Metrologist Release, OU=Mobile, O=cocodedk, C=DK"

  echo "Keystore generated: $KEYSTORE_FILE"
fi

# ── Collect passwords if not already set (reuse path) ───────────────────────
if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
  echo "Enter KEYSTORE_PASSWORD (no double-quotes):"
  stty -echo
  read -r KEYSTORE_PASSWORD
  stty echo
  echo ""
  case "$KEYSTORE_PASSWORD" in
    *'"'*) echo "ERROR: Password must not contain a double-quote."; exit 1 ;;
  esac
fi

if [ -z "${KEY_PASSWORD:-}" ]; then
  echo "Enter KEY_PASSWORD (no double-quotes, Enter to reuse KEYSTORE_PASSWORD):"
  stty -echo
  read -r KEY_PASSWORD
  stty echo
  echo ""
  if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
  fi
  case "$KEY_PASSWORD" in
    *'"'*) echo "ERROR: Key password must not contain a double-quote."; exit 1 ;;
  esac
fi

# ── Verify keystore is readable ─────────────────────────────────────────────
echo "==> Verifying keystore..."
keytool -list \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -storepass "$KEYSTORE_PASSWORD" \
  -noprompt
echo "  Keystore OK."

# ── Upload secrets to GitHub ─────────────────────────────────────────────────
echo "==> Uploading secrets to ${OWNER}/${REPO}..."

KEYSTORE_BASE64=$(base64 -w0 "$KEYSTORE_FILE")

printf '%s' "$KEYSTORE_BASE64"   | gh secret set KEYSTORE_BASE64   --repo "${OWNER}/${REPO}"
printf '%s' "$KEYSTORE_PASSWORD" | gh secret set KEYSTORE_PASSWORD  --repo "${OWNER}/${REPO}"
printf '%s' "$KEY_ALIAS"         | gh secret set KEY_ALIAS          --repo "${OWNER}/${REPO}"
printf '%s' "$KEY_PASSWORD"      | gh secret set KEY_PASSWORD        --repo "${OWNER}/${REPO}"

echo ""
echo "Secrets uploaded:"
echo "  KEYSTORE_BASE64    — base64-encoded $KEYSTORE_FILE"
echo "  KEYSTORE_PASSWORD  — keystore password"
echo "  KEY_ALIAS          — $KEY_ALIAS"
echo "  KEY_PASSWORD       — key password"
echo ""
echo "In your Gradle signing config, decode KEYSTORE_BASE64 back to a file, then reference"
echo "KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD from environment variables or local.properties."
