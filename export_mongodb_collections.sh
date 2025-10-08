#!/bin/bash

# MongoDB Export Script for myprosper database
# This script exports each collection individually as JSON

DATABASE_NAME="myprosper"
OUTPUT_DIR="./mongodb_exports"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# List of collections to export
collections=(
    "advisee_credit"
    "advisee_transactions"
    "advisor_payment_methods"
    "advisor_sessions"
    "advisor_time_slots"
    "advisor_transactions"
    "banks"
    "card_transactions"
    "code_of_conducts"
    "fAQs"
    "feedback"
    "handbook"
    "industries"
    "leadSources"
    "mpesa_transactions"
    "pending_transactions"
    "pending_transactions_vouchers"
    "pricingSetting"
    "selfAssesment"
    "sessions"
    "sms_settings"
    "sms_templates"
    "testimonials"
    "tokens"
    "top_advisors"
    "top_topics"
    "topics"
    "users"
    "vouchers"
    "wallet_settings"
)

echo "Starting MongoDB export for database: $DATABASE_NAME"
echo "Output directory: $OUTPUT_DIR"
echo "=========================================="

# Export each collection
for collection in "${collections[@]}"; do
    echo "Exporting collection: $collection"
    
    mongoexport \
        --db="$DATABASE_NAME" \
        --collection="$collection" \
        --out="$OUTPUT_DIR/${collection}.json" \
        --jsonArray \
        --pretty
    
    if [ $? -eq 0 ]; then
        echo "✅ Successfully exported $collection"
    else
        echo "❌ Failed to export $collection"
    fi
    echo ""
done

echo "=========================================="
echo "Export completed! Files are in: $OUTPUT_DIR"
ls -la "$OUTPUT_DIR"


