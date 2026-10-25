# Account signup and recovery

Signup requires a matching retyped email and three different security answers (4–128 characters each). Passwords remain 12–128 characters. The public welcome page and signup have no admin button or admin setup field; existing admins use normal sign-in. The admin studio link appears only after an admin signs in. The backend reserved-email setup-code protection remains, even though new privileged registrations are not exposed in the UI.

All three questions are fixed for everyone, so requesting them reveals no account existence. Answers are normalized using Unicode NFKC, lowercasing and whitespace collapsing, then combined using JSON with a recovery-specific prefix. Only one salted, peppered PBKDF2 hash of the complete answer bundle is stored in `account_recovery`; no plaintext answers are saved or returned. Correct answers to all three questions are required, with five attempts per account and twenty per IP in fifteen minutes, enforced before hashing. Unknown accounts, wrong answers and accounts without recovery configured return the same message. Recovery settings require the current signed-in password; cross-origin writes are denied.

A successful reset changes only the password, revokes all account sessions, clears the current browser cookie and sends the user to sign-in. Payment status and role are preserved. Password changes use a compare-and-update transaction to reject concurrent password/recovery changes.

Existing accounts cannot recover until they enroll answers while signed in: open `/app` → Set security questions. Android exposes Security questions in the dashboard Account row and for signed-in unpaid users in the activation form. New accounts enroll during signup. No email provider, mailbox or reset email is involved.

Browser eye buttons and Android Show/Hide password buttons reveal only the password field on request. Security answers are masked. Hiding an admin link does not replace authorization; all admin APIs retain role checks.

Android 1.8 is required for new signup fields on TV; previous APKs can still log in to existing accounts. Migration 0006 must be applied before deploying the new auth worker.
