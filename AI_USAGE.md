# AI_USAGE.md

**Project:** CampusBite — Group 02
**Course:** 72ITSE30603 Mobile Application Programming, Van Lang University
**Team:** Lưu Gia Bảo (leader), Trần Quang Minh, Nguyễn Trung Đức, Nguyễn Dũng
**Date:** 22/11/2026

## Tool

We used **DeepSeek** as a coding assistant during the project.

## How we used it

Mostly for the repetitive parts: Gradle setup, Room entities/DAOs and migration SQL, the Retrofit/DTO layer, Firebase Auth and Firestore wiring, some ViewBinding/RecyclerView code, test skeletons, and first drafts of the README and comments.

We did not use it to decide requirements, split the work, or write our commits. Every suggestion was read by the member who owned that part, adjusted where needed, and only kept if it fit the architecture.

## What we checked

- Built the app in debug and release (`assembleDebug`, `assembleRelease`, `bundleRelease`).
- Ran the unit tests (8) and the instrumented tests (migration, repository, UI journey — 11).
- Installed the signed APK and walked through the student, vendor and manager demo flows.
- Ran Android Lint; the release build passes `lintVital` with no errors.

A few AI suggestions were rejected, for example destructive Room migrations, letting a user pick a vendor/manager role at registration, and storing passwords in the saved instance state.

## Privacy

We never put the keystore, its passwords, `local.properties`, real student data or credentials into a prompt. Only the public client-side Firebase config was involved.

## Declaration

AI was a tool, not a replacement for our own work. All submitted code was reviewed and tested by the team, and each member can explain their part during the oral defence.

**Signed:** Lưu Gia Bảo, Trần Quang Minh, Nguyễn Trung Đức, Nguyễn Dũng — Group 02, 22/11/2026.
