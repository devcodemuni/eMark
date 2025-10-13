# eMark | Professional PDF Signer with USB Token, PFX & Windows Certificate Support

<div align="center">
  <img src="src/main/resources/icons/logo.png" alt="eMark – Free PDF Signing Software" width="120">

  <p>
    <img src="https://img.shields.io/badge/Java-1.8-007396?logo=java&logoColor=white" alt="Java 8">
    <img src="https://img.shields.io/badge/License-AGPL%203.0-brightgreen" alt="AGPL 3.0 License">
    <img src="https://img.shields.io/badge/Platform-Windows%20|%20Linux%20|%20macOS-brightgreen" alt="Cross-Platform">
    <img src="https://img.shields.io/badge/Version-1.1.0-blue" alt="Version 1.1.0">
    <img src="https://img.shields.io/github/downloads/devcodemuni/eMark/total?color=success&label=Downloads" alt="Total Downloads">
  </p>
</div>

---

## 📝 About eMark

**eMark** is a free and open-source **PDF signing and verification application** – a powerful Adobe Reader alternative that enables you to securely sign, verify, timestamp, and protect PDF documents using:

- 🔑 **USB tokens and HSM (PKCS#11)**
- 📜 **PKCS#12/PFX certificates**
- 💻 **Windows certificate store**

Works seamlessly on **Windows, Linux, and macOS** with an Adobe Reader-like interface featuring professional signature verification capabilities. Built for individuals, enterprises, and government organizations.

> 🏆 **Perfect for:** Digital signature compliance (DSC), tender signing, invoices, contracts, secure document authentication, and signature verification.

---

## 🚀 Key Features

### ✅ **PDF Signature Verification**
- 🔍 Verify digital signatures in PDF documents
- 🛡️ Certificate validation and trust chain verification
- 🔐 Signature integrity checks with detailed status
- 📊 Comprehensive certificate information display
- 🤝 Adobe Reader-compatible verification
- 🏷️ Visual signature status indicators (valid/invalid/unknown)

### 🖥️ **Adobe Reader-like Interface**
- 🎯 Automatic signature field detection
- 👁️ Visual signature overlays with color-coded status
- 🎨 Interactive signature fields with click-to-verify
- 🔘 Floating signature action button
- 📐 Collapsable signature properties panel
- 🌙 Modern dark theme with FlatMacDarkLaf

### 🔐 **Multiple Signing Methods**
- 🔌 USB token & HSM support (PKCS#11)
- 📁 PKCS#12/PFX file support
- 🏢 Windows certificate store integration
- 🔑 Smart card PIN/password management
- 🔒 Secure key storage

### 🌐 **Cross-Platform Support**
- ✔️ Windows 7 or later (64-bit)
- ✔️ Linux (Debian/Ubuntu 18.04+)
- ✔️ macOS (JAR version)
- 📦 Native installers & executable JAR

### 🛡️ **Enterprise-Grade Security**
- ⏱️ RFC 3161 timestamping support
- 📜 LTV (Long-Term Validation)
- 🔒 Password-protected PDF support
- 🔑 Trust certificate management
- 🔐 Certificate chain validation
- 🛡️ Secure signature algorithms (SHA-256, SHA-512)

### 🎨 **Modern User Experience**
- 🌙 Consistent dark theme (FlatMacDarkLaf)
- ✋ Drag-and-drop signature placement
- 👁️ Live signature preview
- 📐 Collapsable signature properties panel
- 🎯 Enhanced customization options
- 🖼️ Custom signature image support

### 💡 **Open Source & Free**
- 📄 Licensed under AGPL 3.0
- 🤝 Contributions and forks welcome
- 🔓 No vendor lock-in
- 🌍 Community-driven development

---

## 🛠️ Getting Started

### 📋 Prerequisites

- **☕ Java SE 8 (JDK or JRE)** – Required
  > ⚠️ **Important:** Only Java 8 (1.8.x) is supported. Not compatible with Java 7 or Java 9+

- 🌍 **Supported Operating Systems:**
  - 🪟 Windows 7 or later (64-bit recommended)
  - 🐧 Linux Ubuntu 18.04+ / Debian 10+
  - 🍎 macOS (JAR version only)

- 📜 **Digital Signing Certificate** (one of the following):
  - USB token (eToken, SafeNet, etc.)
  - Hardware Security Module (HSM)
  - PFX/PKCS#12 certificate file

---

### ⬇️ Installation

#### **Option 1 – Download Latest Release**
[![Download Latest eMark](https://img.shields.io/github/v/release/devcodemuni/eMark?style=for-the-badge&color=blue)](https://github.com/devcodemuni/eMark/releases/latest)

1. 📥 Download the latest release for your platform
2. ⚙️ Install and launch the application
3. 🖊️ Start signing and verifying PDFs securely

#### **Option 2 – Build from Source**
```bash
# Clone the repository
git clone https://github.com/devcodemuni/eMark.git
cd eMark

# Build with Maven
mvn clean package

# Run the application
java -jar target/eMark-1.1.0-SNAPSHOT.jar
```

---

## 🖥️ How to Use

### 📝 Signing PDFs

1. 🚀 **Launch eMark** and open your PDF document
2. 🖱️ **Click "Begin Sign"** and select the signing area (or click on detected signature fields)
3. 🔑 **Choose your certificate** from:
   - USB token/HSM
   - Windows certificate store
   - PFX/PKCS#12 file
4. 🎨 **Customize signature appearance** in the collapsable properties panel:
   - Signature text and reason
   - Date format
   - Custom image
   - Position and size
5. 🔒 **Enter PIN/password** when prompted
6. ✍️ **Click "Sign"** and save your signed PDF

### 🔍 Verifying Signatures

1. 📂 **Open a signed PDF** document
2. 🎯 **Signatures are auto-detected** and displayed with visual overlays
3. 🖱️ **Click any signature** to view verification details:
   - Certificate information
   - Trust chain status
   - Signature validity
   - Timestamp details
4. ✅ **Review verification results** with color-coded indicators:
   - 🟢 Green = Valid & Trusted
   - 🟡 Yellow = Valid but Untrusted
   - 🔴 Red = Invalid
5. 🔐 **Manage trusted certificates** in Settings → Trust Certificates

### ⚙️ Configuration

- **PKCS#11 Libraries:** Configure USB token/HSM paths in Settings
- **Proxy Settings:** Configure HTTP/HTTPS proxy for timestamp servers
- **Trust Certificates:** Add/remove trusted CA certificates
- **Keystore Settings:** Manage certificate sources

---

## 📸 Screenshots & Documentation

* [📸 View Screenshots](docs/image-gallery.md)
* [🗺️ Architecture & Signing Workflow](docs/diagram.md)

---

## 🏗️ Project Structure

```
eMark/
├── src/main/java/com/codemuni/
│   ├── config/          # Configuration management
│   ├── controller/      # Application controllers
│   ├── core/
│   │   ├── exception/   # Custom exceptions
│   │   ├── keyStoresProvider/  # Certificate providers
│   │   ├── model/       # Data models
│   │   └── signer/      # Signing logic
│   ├── gui/
│   │   ├── pdfHandler/  # PDF viewer & signature UI
│   │   └── settings/    # Settings dialogs
│   ├── service/         # Business services
│   └── utils/           # Utilities & constants
└── src/main/resources/
    ├── icons/           # Application icons
    └── config/          # Default configurations
```

---

## 🔧 Troubleshooting

### 🚨 Common Issues

**⚠️ Java Version Error**
```bash
# Check your Java version
java -version

# Should output: java version "1.8.0_xxx"
```

**🔑 Certificate Not Detected**
- Verify USB token drivers are installed
- Check certificate validity and expiration
- Ensure PKCS#11 library path is correct

**📄 PDF Loading Issues**
- Verify PDF is not corrupted
- Check if PDF is password-protected
- Ensure file permissions allow reading

**🔐 Signature Verification Failed**
- Add CA certificate to trust store
- Check internet connection for OCSP/CRL
- Verify certificate chain is complete

**🖥️ UI Display Issues**
- Ensure Java 8 is installed (not Java 9+)
- Update graphics drivers
- Try running with `-Dsun.java2d.opengl=true`

---

## 🤝 Contributing

✨ **We welcome contributions!** 🎉

Your ideas, bug fixes, and improvements make this project better. Here's how you can contribute:

### 📋 **Before Contributing**
- Read our [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines
- Check existing issues and pull requests
- Follow our coding standards and commit conventions

### 🐛 **Reporting Bugs**
- Use the issue tracker with a clear title
- Include steps to reproduce
- Provide error logs and system info

### 💡 **Suggesting Features**
- Explain the use case clearly
- Describe expected behavior
- Consider backward compatibility

### 🚀 **Submitting Pull Requests**
- Fork and create a feature branch
- Write clear, concise commit messages
- Update documentation as needed
- Ensure all tests pass

---

## 🔒 Security

Found a security vulnerability? Please **do not** open a public issue. Instead:
- Email: [security contact needed]
- Provide detailed information
- Allow time for a fix before disclosure

---

## 📜 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

See [LICENSE](LICENSE) for full details.

### What this means:
- ✅ Free to use, modify, and distribute
- ✅ Open source code must remain open
- ✅ Network use requires source availability
- ⚠️ Commercial use allowed with conditions

---

## 🙏 Acknowledgments

- **FlatLaf** - Modern look and feel library
- **Apache PDFBox** - PDF rendering engine
- **iText 5** - PDF signing capabilities
- **Bouncy Castle** - Cryptography provider

---

## 📧 Contact & Community

* 💻 **GitHub:** [devcodemuni/eMark](https://github.com/devcodemuni/eMark)
* 🐛 **Issues & Support:** [Open an Issue](https://github.com/devcodemuni/eMark/issues)
* 💬 **Discussions:** [GitHub Discussions](https://github.com/devcodemuni/eMark/discussions)
* 📖 **Documentation:** [Wiki](https://github.com/devcodemuni/eMark/wiki)

---

<div align="center">

  **Made with ❤️ for secure PDF signing and open-source freedom**

  ⭐ Star us on GitHub | 🐛 Report Issues | 🤝 Contribute

  © 2025 eMark Project | AGPL-3.0 License

</div>
