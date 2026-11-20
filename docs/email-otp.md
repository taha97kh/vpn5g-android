# Email OTP — Persian email texts (VPN5G IRBOT v2)

The v1 auth screens (`Vpn5gAuthScreens.kt`) are UI-only: the send/confirm
buttons show "به‌زودی — نیاز به سرور دارد (نسخه‌ی بعد)" because there is no
backend yet. Sending real codes needs **Supabase Auth** (email OTP) in v2,
plus a support email address from the admin — **provided 2026-11-20:
`VPN5G.IRBOT@gmail.com`** (the bot's email; all verification codes are sent
from this address, sender name `VPN5G IRBOT`).

## What v2 needs

1. A Supabase project with Email Auth enabled (OTP / magic-link templates
   use the texts below).
2. A support/sender email address — **provided: `VPN5G.IRBOT@gmail.com`**
   (the bot's email; sender name `VPN5G IRBOT`).
3. The app's auth screens wired to `supabase.auth.signInWithOtp(email)` and
   `verifyOtp(email, token)`; the 6-digit code field already validates the
   format in v1.

## Registration code email

- Subject: `کد تایید ثبت‌نام در VPN5G IRBOT`
- Body (plain text, RTL):

```
سلام،

به VPN5G IRBOT خوش آمدید!

کد تایید شما برای ثبت‌نام:

123456

این کد تا ۱۰ دقیقه معتبر است. اگر شما این درخواست را نداده‌اید، این ایمیل را نادیده بگیرید.

پشتیبانی: @Aref_5G
ربات: @VPN5G_IRBot
```

(Replace `123456` with the real 6-digit code; expiry 10 minutes.)

## Password-recovery code email

- Subject: `کد بازیابی رمز عبور VPN5G IRBOT`
- Body (plain text, RTL):

```
سلام،

شما درخواست بازیابی رمز عبور حساب VPN5G IRBOT خود را داده‌اید.

کد بازیابی شما:

123456

این کد تا ۱۰ دقیقه معتبر است. اگر شما این درخواست را نداده‌اید، رمز عبور شما تغییر نکرده است و می‌توانید این ایمیل را نادیده بگیرید.

پشتیبانی: @Aref_5G
ربات: @VPN5G_IRBot
```

## Notes

- Registration is Gmail-only (v1.1): signup accepts only `@gmail.com`
  addresses; disposable / temporary-mail domains are rejected. The app
  enforces this locally (`Vpn5gEmailPolicy`); Supabase should also be
  configured to allow only gmail.com signups if its settings permit it.

- Never send a password or a reset link in these emails — only the 6-digit
  code, valid 10 minutes, single use.
- The sender name should be `VPN5G IRBOT`; the sender address is
  `VPN5G.IRBOT@gmail.com` (provided by the admin 2026-11-20).
- Supabase email templates support `{{ .Token }}` for the code — paste the
  body above into the Supabase Auth email template with the token in place
  of the example digits.
