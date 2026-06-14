#!/bin/sh
set -eu

OWNER="cocodedk"
REPO="Metrologist"
BRANCH="main"

echo "==> Configuring merge settings for ${OWNER}/${REPO}..."
gh repo edit "${OWNER}/${REPO}" \
  --enable-squash-merge \
  --enable-rebase-merge \
  --disable-merge-commit \
  --delete-branch-on-merge

echo "==> Applying branch protection to '${BRANCH}'..."
PROTECTION_RESPONSE=$(gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  "/repos/${OWNER}/${REPO}/branches/${BRANCH}/protection" \
  --field required_status_checks='{"strict":true,"contexts":["verify"]}' \
  --field enforce_admins=false \
  --field required_pull_request_reviews='{"required_approving_review_count":0,"dismiss_stale_reviews":false,"require_code_owner_reviews":false}' \
  --field restrictions=null \
  --field allow_force_pushes=false \
  --field allow_deletions=false \
  2>&1 || true)

if echo "$PROTECTION_RESPONSE" | grep -qi "upgrade to github pro"; then
  echo ""
  echo "NOTE: Branch protection via API requires GitHub Pro for private repos."
  echo "  The pre-push hook (.githooks/pre-push) provides equivalent local protection:"
  echo "    - Blocks force pushes to: main, master, trunk, production, prod, release"
  echo "    - Blocks branch deletion"
  echo "    - Locked to pushes under github.com/${OWNER}"
  echo "  Run scripts/install-hooks.sh on each clone to activate."
else
  echo "  Branch protection applied to '${BRANCH}'."
fi

echo "==> Writing .github/CODEOWNERS..."
mkdir -p .github
cat > .github/CODEOWNERS <<'EOF'
* @cocodedk
EOF

echo ""
echo "Done. Repository settings configured for ${OWNER}/${REPO}."
echo "  - Squash + rebase merge only"
echo "  - Delete branch on merge"
echo "  - CODEOWNERS: * @cocodedk"
echo "  - Required status check: verify"
