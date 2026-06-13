# 📱 BYOK OS (Bring Your Own Key Operating System)

[![AISTUDIO Build](https://img.shields.io/badge/AI_Studio-Build-blueviolet?style=flat-square)](https://ai.studio/build)
[![App Size](https://img.shields.io/badge/Size-14.97_MB-green?style=flat-square)](app-debug.apk)
[![Android Support](https://img.shields.io/badge/Android-5.0%2B_--_API_24%2B-blue?style=flat-square)](app/src/main/AndroidManifest.xml)

---

## 🚨 [বাংলা] কেন "Parse Page / You can't install the app" ইরর আসে এবং সমাধান:
আপনি যখন আপনার মোবাইল ব্রাউজারে GitHub রিপোজিটরি ওপেন করে সরাসরি `app-debug.apk` ফাইলের নামের উপর ক্লিক করেন, তখন **GitHub আপনাকে আসল APK ফাইলটি দেয় না**। বরং GitHub একটি **ওয়েব প্রিভিউ HTML পেজ** দেখায়। 

যদি আপনি এই প্রিভিউ পেজটি ডাউনলোড করে ফেলেন, তবে তার সাইজ হয় মাত্র **১০০-২০০ KB (বা ৩.৭ MB)** এবং এটি একটি টেক্সট/ওয়েবপেজ ফাইল হয়। তাই ফোনে ইনস্টল করার সময় অ্যান্ড্রয়েড বলে: **"There was a problem while parsing the package"** অথবা **"You can't install the app"**।

### অরিজিনাল APK (14.97 MB) সরাসরি ডাউনলোড করার শতভাগ কার্যকর উপায়:

#### ১. সরাসরি র লিঙ্ক (Direct Raw Download Link) ফর্মুলা:
নিচের লিঙ্কটি কপি করুন, এরপর আপনার ব্রাউজারের অ্যাড্রেস বারে পেস্ট করে জাস্ট **`[YOUR_GITHUB_USERNAME]`** এবং **`[YOUR_REPO_NAME]`** এর জায়গায় আপনার রিয়েল ইউজারনেম ও রিপোজিটরি নেম বসিয়ে দিন। সরাসরি ১ সেকেন্ডে রিয়েল ১৪.৯৭ মেগাবাইট APK ডাউনলোড শুরু হয়ে যাবে!
```text
https://github.com/[YOUR_GITHUB_USERNAME]/[YOUR_REPO_NAME]/raw/main/app-debug.apk
```
*অথবা:*
```text
https://raw.githubusercontent.com/[YOUR_GITHUB_USERNAME]/[YOUR_REPO_NAME]/main/app-debug.apk
```

#### ২. গিটহাব ব্রাউজার থেকে ডাউনলোড করার নিয়ম:
1. ব্রাউজারে আপনার গিটহাব রিপোজিটরির মেইন পেজে যান।
2. লিস্টে থাকা **`app-debug.apk`** ফাইলের উপর ক্লিক করুন।
3. ক্লিক করার পর যে পেজটি আসবে, সেখানে উপরে ডানপাশে বা মাঝখানে একটি **`Download`** বা **`Download raw file`** বা **`Raw`** লেখা বাটন দেখতে পাবেন (নিচের দিকে মুখ করা তির চিহ্ন থাকতে পারে)।
4. সেখানে ক্লিক করে আসল **14.97 MB** সাইজের ফাইলটি ডাউনলোড করুন। ডাউনলোডের প্রোগ্রেস বারে সাইজ চেক করে নিশ্চিত হোন।

---

## 🚨 [English] Why "Parse Page / You can't install the app" Error Happens & The Solution:
When you open a GitHub repository on your mobile browser and tap on `app-debug.apk`, **GitHub does not immediately download the raw binary file**. Instead, it loads a **webpage preview HTML source code**.

If you download that preview page, the downloaded file is a text/HTML webpage (usually has a corrupted format or wrong size like a few KB). When you try to install this on Android, it outputs: **"There was a problem while parsing the package"** or **"You can't install..."**.

### 100% Working Way to Download the Real APK (14.97 MB):

#### Method 1: The Direct Raw URL Formula (Easiest & Safest)
Simply copy the URL below, open a new Chrome tab on your phone, and replace the brackets with your actual GitHub username and repository name. This triggers a direct binary stream download immediately:
```text
https://github.com/[YOUR_GITHUB_USERNAME]/[YOUR_REPO_NAME]/raw/main/app-debug.apk
```
*Alternative Raw Endpoint:*
```text
https://raw.githubusercontent.com/[YOUR_GITHUB_USERNAME]/[YOUR_REPO_NAME]/main/app-debug.apk
```

#### Method 2: Tap the "Download Raw File" Button on GitHub
1. Open this repository on your mobile web browser (e.g., Chrome).
2. Tap on the **`app-debug.apk`** file from the file list at the root.
3. Once the preview screen loads, locate and tap the **"Download raw file"** / **"Download"** button on the right-hand panel (usually styled as a download icon or explicitly named).
4. Verify that the file size showing in your browser's download manager is exactly **~14.97 MB** (not a small file size!).

---

## 🛠️ Installation & Verification

### A. Size Check
- **Incorrect/Webpage File**: `~120 KB` - `3.7 MB` (Do not install! This is the GitHub web-viewer code).
- **Correct/Binary App File**: **`14.97 MB`** (The authentic Android application).

### B. Allow Unknown App Sources
Since this is a custom-compiled application and is not published on the Google Play Store, Android will request permission to install unknown apps:
1. Once download is complete, open the file.
2. If prompted, tap **Settings** and toggle **"Allow from this source"** for Chrome/your web browser/your File Manager.
3. Tap **Install** and enjoy **BYOK OS**!
