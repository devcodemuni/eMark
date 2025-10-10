# eMark | Open Source PDF Signer with USB Token, PFX, and Windows Certificate Support

<div align="center">
  <img src="src/main/resources/icons/logo.png" alt="eMark – Free PDF Signing Software" width="120">

  <p>
    <img src="https://img.shields.io/badge/Java-1.8%2B-007396?logo=java&logoColor=white" alt="Java 8+">
    <img src="https://img.shields.io/badge/License-AGPL%203.0-brightgreen" alt="AGPL 3.0 License">
    <img src="https://img.shields.io/badge/Platform-Windows%20|%20Linux%20|%20macOS-brightgreen" alt="Cross-Platform">
    <img src="https://img.shields.io/badge/Version-1.1.0-blue" alt="Version 1.1.0">
    <img src="https://img.shields.io/github/downloads/devcodemuni/eMark/total?color=success&label=Downloads" alt="Total Downloads">
  </p>
</div>

---

## 📝 About eMark

**eMark** ✨ is a free and open-source **PDF signing and verification software** – a powerful Adobe Reader alternative that allows you to securely sign, verify, timestamp, and protect PDF documents using:

- 🔑 **USB tokens and HSM (PKCS#11)**
- 📜 **PKCS#12/PFX certificates**
- 💻 **Windows certificate store**

It works on **Windows, Linux, and macOS**, features an Adobe Reader-like interface with professional signature verification capabilities, and is built for individuals, enterprises, and government use.

> 🏆 Ideal for: **Digital signature compliance (DSC), tender signing, invoices, contracts, secure document authentication, and signature verification.**

---

## 🚀 Key Features

- **✅ PDF Signature Verification**
    - 🔍 Verify digital signatures in PDF documents
    - 🛡️ Certificate validation and trust chain verification
    - 🔐 Signature integrity checks
    - 📊 Detailed certificate information display
    - 🤝 Adobe Reader-compatible verification

- **🖥️ Adobe Reader-like Interface**
    - 🎯 Automatic signature field detection
    - 👁️ Visual signature overlays with color-coded status
    - 🎨 Interactive signature fields
    - 🔘 Floating signature action button

- **🔐 Multiple Signing Methods**
    - 🔌 USB token & HSM support (PKCS#11)
    - 📁 PKCS#12/PFX file support
    - 🏢 Windows certificate store integration

- **🌐 Cross-Platform**
    - ✔️ Works on Windows, Linux (Debian/Ubuntu), macOS (JAR version)
    - 📦 Executable JAR & native installers

- **🛡️ Enterprise-Grade Security**
    - ⏱️ Timestamping support
    - 📜 LTV (Long-Term Validation)
    - 🔒 Password-protected PDF support
    - 🔑 Trust certificate management

- **🎨 Modern User Interface**
    - 🌙 Dark theme with modern design
    - ✋ Drag-and-drop signature placement
    - 👁️ Live signature preview
    - 📐 Collapsable signature properties panel
    - 🎯 Enhanced customization options

- **💡 Open Source & Free**
    - 📄 Licensed under AGPL 3.0
    - 🤝 Contributions and forks are welcome

---

## 🛠️ Getting Started

### 📋 Prerequisites

- **☕ Java SE 8 (JDK or JRE)** – required
  > ⚠️ Not compatible with Java 7 or Java 9+

- 🌍 Supported operating systems:
    - 🪟 Windows 7 or later (64-bit)
    - 🐧 Linux Ubuntu 18.04+ / Debian
    - 🍎 macOS (JAR version only)

- 📜 A valid **digital signing certificate** (USB token, HSM, or PFX/PKCS#12)

---

### ⬇️ Installation

#### **Option 1 – Download Latest Release**
[![Download Latest eMark](https://img.shields.io/github/v/release/devcodemuni/eMark?style=for-the-badge&color=blue)](https://github.com/devcodemuni/eMark/releases/latest)

1. 📥 Download the latest release for your platform
2. ⚙️ Install and launch the application
3. 🖊️ Start signing PDFs securely

#### **Option 2 – Build from Source**
```bash
git clone https://github.com/devcodemuni/eMark.git
cd eMark
mvn clean package
java -jar target/eMark-1.1.0-SNAPSHOT.jar
```

---

## 🖥️ How to Use

### Signing PDFs
1. 🚀 Launch eMark
2. 📂 Open your PDF document
3. 🖱️ Click **"Begin Sign"** and select the signing area (or click detected signature fields)
4. 🔑 Choose your certificate (USB token, HSM, or PFX)
5. 🎨 Customize signature appearance in the collapsable panel
6. 🔒 Enter your password or PIN if required
7. ✍️ Click **"Sign"** and save the signed PDF

### Verifying Signatures
1. 📂 Open a signed PDF document
2. 🔍 Signature fields are automatically detected and displayed
3. 🖱️ Click on any signature field to view verification details
4. ✅ Review certificate information, trust status, and signature integrity
5. 🔐 Manage trusted certificates in Settings

---

## 📸 Screenshots & Documentation

* [📸 View Screenshots](docs/image-gallery.md)
* [🗺️ Architecture & Signing Workflow](docs/diagram.md)

---

## 🔧 Troubleshooting

### 🚨 Common Issues

* **⚠️ Java Version Error:** Run `java -version` and ensure it's Java 8
* **📄 PDF Loading Issues:** Ensure the file is not corrupted or locked
* **🔑 Certificate Not Detected:** Verify your token drivers and certificate validity

---

## 🤝 Contributing


✨ **We welcome contributions!** 🎉  
Your ideas, fixes, and improvements help make this project awesome. Here’s how you can get involved:

📄 **Read the guidelines**  
Before you start, please check out our [CONTRIBUTING.md](CONTRIBUTING.md) to learn about our code of conduct, coding standards, and the pull request process.

🚀 **Submit a Pull Request**  
Found a bug? Added a feature? We’d love to see it! Open a PR and join our community of contributors 💪

---

## 📜 License

Licensed under **AGPL 3.0** – see [LICENSE](LICENSE) for details. 📄

---

## 📧 Contact & Community

* 💻 GitHub: [devcodemuni/eMark](https://github.com/devcodemuni/eMark)
* 🆘 Issues & Support: [Open an Issue](https://github.com/devcodemuni/eMark/issues)

---

<div align="center">
  Made with ❤️ for secure PDF signing and open-source freedom. ✨
</div>