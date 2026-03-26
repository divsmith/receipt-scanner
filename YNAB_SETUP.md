# YNAB Setup Guide

## Overview

This app integrates with [YNAB (You Need A Budget)](https://www.ynab.com) to submit scanned receipt transactions. You need a YNAB account and a Personal Access Token.

## Getting a Personal Access Token

1. Log in to YNAB at [https://app.ynab.com](https://app.ynab.com)
2. Click your account name (top-left) → **Account Settings**
3. Scroll down to **"Developer Settings"**
4. Click **"New Token"**
5. Enter your password to confirm
6. Copy the generated token immediately (it won't be shown again!)

> ⚠️ **Important:** Treat your token like a password. It provides full access to your YNAB data.

## Entering the Token in the App

1. Open Receipt Scanner
2. Tap the ⚙️ Settings icon (top-right of camera screen)
3. Paste your Personal Access Token in the **"YNAB API Token"** field
4. The app will validate the token and fetch your budgets
5. Select your budget from the dropdown
6. The app will sync your accounts, payees, and categories

Your token is stored securely using Android's `EncryptedSharedPreferences`.

## Setting Up Account Matching

The app can automatically detect which account to use based on the last 4 digits of the card number on your receipt.

### How to Configure

1. In YNAB, go to the account you want to match
2. Click the account name to open account settings
3. In the **Notes** field, add: `Last4: 1234` (replace `1234` with your actual card last 4 digits)
4. You can add multiple cards: `Last4: 1234, 5678`
5. Save the account settings
6. In Receipt Scanner, go to Settings and trigger a sync (or it will sync automatically)

### How It Works

- When the OCR extracts a card number ending in "1234" from a receipt
- The app searches your synced accounts for a matching `Last4:` note
- The matching account is auto-selected on the review screen
- You can always override the selection manually

## API Rate Limits

YNAB enforces rate limits on their API:

| Limit              | Value          |
| ------------------- | -------------- |
| Requests per hour   | 200            |
| Rate limit header   | `X-Rate-Limit` |

The app uses delta sync to minimize API calls — after the initial sync, only changes are fetched using `server_knowledge` tokens. Normal usage should stay well within limits.

### Tips to Stay Within Limits

- The app caches payees, categories, and accounts locally
- Delta sync only fetches changes since last sync
- Avoid excessive manual syncs
- If you hit the limit, wait for the hour to reset

## Data Sync

### What Gets Synced

| Data         | Direction    | Cache Duration                        |
| ------------ | ------------ | ------------------------------------- |
| Budgets      | YNAB → App   | Refreshed on settings change          |
| Accounts     | YNAB → App   | Delta sync with `server_knowledge`    |
| Payees       | YNAB → App   | Delta sync with `server_knowledge`    |
| Categories   | YNAB → App   | Delta sync with `server_knowledge`    |
| Transactions | App → YNAB   | Submitted, then queued if offline     |

### Delta Sync

The app stores a `server_knowledge` value for each data type. On each sync, it sends this value to YNAB and only receives records that changed since the last sync. This dramatically reduces API usage and sync time.

## Currency Format

YNAB uses **milliunits** for all amounts:

- 1 dollar = 1,000 milliunits
- $12.50 = 12,500 milliunits
- Outflows (purchases) are **negative**: -$12.50 = -12,500 milliunits

The app handles this conversion automatically — you enter amounts in normal dollar format on the review screen.

## Troubleshooting

### "Invalid Token" Error

- Verify you copied the full token (no leading/trailing spaces)
- Check that the token hasn't been revoked in YNAB settings
- Generate a new token if needed

### "No Budgets Found"

- Ensure you have at least one active budget in YNAB
- Check your internet connection
- Try refreshing from the Settings screen

### Sync Not Working

- Check internet connectivity
- Verify the token is still valid
- Check if you've hit the API rate limit (200 req/hour)
- Try clearing the cache and re-syncing from Settings

### Account Not Auto-Detected

- Verify the account notes contain `Last4: XXXX` (exact format)
- Ensure the receipt's card number was correctly read by OCR
- Check that the account has been synced (pull to refresh in Settings)

### Transactions Not Appearing in YNAB

- Check the pending queue in Receipt History
- Failed transactions show error messages — tap to retry
- Ensure internet connectivity for submission
- Offline transactions are automatically retried via WorkManager
