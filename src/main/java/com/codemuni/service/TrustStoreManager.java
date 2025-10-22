package com.codemuni.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.nio.file.*;
import java.security.cert.*;
import java.util.*;

/**
 * Manages trusted certificate stores for signature verification.
 * Supports TWO sources of trust:
 * 1. Embedded certificates (resources/trusted-certs/) - read-only
 * 2. Manual certificates (user.home/.emark/trusted-certs/) - user-managed
 *
 * Note: OS trust stores (Windows, macOS, Linux) are NOT used for signature verification.
 */
public class TrustStoreManager {

    private static final Log log = LogFactory.getLog(TrustStoreManager.class);

    // Embedded certificates path (in resources)
    private static final String EMBEDDED_CERTS_PATH = "/trusted-certs/";

    // User certificates directory
    private static final String USER_CERTS_DIR = System.getProperty("user.home") +
            File.separator + ".emark" + File.separator + "trusted-certs";

    private static TrustStoreManager instance;

    // Cached certificates
    private Set<X509Certificate> embeddedCertificates;
    private Map<String, X509Certificate> manualCertificates; // alias -> certificate
    private boolean initialized = false;

    private TrustStoreManager() {
        embeddedCertificates = new HashSet<>();
        manualCertificates = new LinkedHashMap<>();
    }

    /**
     * Gets the singleton instance.
     */
    public static synchronized TrustStoreManager getInstance() {
        if (instance == null) {
            instance = new TrustStoreManager();
        }
        return instance;
    }

    /**
     * Initializes the trust store manager.
     * Loads all certificates from all sources.
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        log.info("Initializing Trust Store Manager...");

        // Load embedded certificates
        loadEmbeddedCertificates();

        // Load manual certificates
        loadManualCertificates();

        initialized = true;
        log.info("Trust Store Manager initialized successfully");
        log.info("Total trust certificates: " +
                "Embedded=" + embeddedCertificates.size() +
                ", Manual=" + manualCertificates.size());
    }

    /**
     * Loads embedded (read-only) certificates from resources.
     * Dynamically scans the trusted-certs resource directory for all certificate files.
     */
    private void loadEmbeddedCertificates() {
        embeddedCertificates.clear();

        try {
            log.info("Loading embedded certificates from resources...");

            // Get all certificate files from resources/trusted-certs/
            // Use ClassLoader to list resources
            java.net.URL resourceUrl = getClass().getResource(EMBEDDED_CERTS_PATH);

            if (resourceUrl != null) {
                java.net.URI uri = resourceUrl.toURI();
                Path certsPath;

                if (uri.getScheme().equals("jar")) {
                    // Running from JAR - use FileSystem
                    try {
                        java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(uri, new HashMap<>());
                        certsPath = fs.getPath(EMBEDDED_CERTS_PATH);
                    } catch (java.nio.file.FileSystemAlreadyExistsException e) {
                        certsPath = java.nio.file.FileSystems.getFileSystem(uri).getPath(EMBEDDED_CERTS_PATH);
                    }
                } else {
                    // Running from IDE/filesystem
                    certsPath = Paths.get(uri);
                }

                // List all certificate files
                java.util.stream.Stream<Path> fileStream = Files.walk(certsPath, 1);
                fileStream.forEach(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    if (fileName.endsWith(".pem") || fileName.endsWith(".der") ||
                        fileName.endsWith(".cer") || fileName.endsWith(".crt") ||
                        fileName.endsWith(".p7b") || fileName.endsWith(".p7c")) {

                        String resourcePath = EMBEDDED_CERTS_PATH + path.getFileName().toString();
                        try {
                            loadEmbeddedCertificate(resourcePath);
                            log.debug("Loaded embedded cert: " + path.getFileName());
                        } catch (Exception e) {
                            log.warn("Could not load embedded cert: " + path.getFileName() + " - " + e.getMessage());
                        }
                    }
                });
                fileStream.close();

            } else {
                log.warn("Could not find embedded certificates resource directory: " + EMBEDDED_CERTS_PATH);
            }

        } catch (Exception e) {
            log.error("Error loading embedded certificates", e);
        }

        log.info("Loaded " + embeddedCertificates.size() + " embedded certificate(s)");
    }

    /**
     * Loads embedded certificate(s) from resources.
     * Supports files with multiple certificates (e.g., PKCS#7 bundles).
     */
    private void loadEmbeddedCertificate(String resourcePath) throws Exception {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }

        try {
            // Parse certificates - may return multiple certs from one file
            List<X509Certificate> certs = parseCertificatesFromStream(is);

            embeddedCertificates.addAll(certs);
        } finally {
            is.close();
        }
    }

    /**
     * Loads manual (user-added) certificates from user directory.
     */
    private void loadManualCertificates() {
        manualCertificates.clear();

        File certsDir = new File(USER_CERTS_DIR);
        if (!certsDir.exists()) {
            log.info("Manual certificates directory does not exist, creating: " + USER_CERTS_DIR);
            certsDir.mkdirs();
            return;
        }

        File[] certFiles = certsDir.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".pem") || lowerName.endsWith(".der") ||
                   lowerName.endsWith(".cer") || lowerName.endsWith(".crt") ||
                   lowerName.endsWith(".p7b") || lowerName.endsWith(".p7c") ||
                   lowerName.endsWith(".spc");
        });

        if (certFiles == null || certFiles.length == 0) {
            log.info("No manual certificates found");
            return;
        }

        log.info("Loading " + certFiles.length + " manual certificate(s)...");

        for (File certFile : certFiles) {
            try {
                // Parse certificate(s) from file - may contain multiple certs
                List<X509Certificate> certs = parseCertificatesFromFile(certFile);

                if (certs.isEmpty()) {
                    log.warn("No certificates found in file: " + certFile.getName());
                    continue;
                }

                // If file contains multiple certificates, add each with index
                if (certs.size() == 1) {
                    String alias = certFile.getName();
                    manualCertificates.put(alias, certs.get(0));
                    log.info("Loaded manual certificate: " + alias + " - " + certs.get(0).getSubjectDN());
                } else {
                    // Multiple certificates in one file (e.g., PKCS#7 bundle)
                    for (int i = 0; i < certs.size(); i++) {
                        String alias = certFile.getName() + "[" + i + "]";
                        manualCertificates.put(alias, certs.get(i));
                        log.info("Loaded manual certificate: " + alias + " - " + certs.get(i).getSubjectDN());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load manual certificate: " + certFile.getName(), e);
            }
        }

        log.info("Loaded " + manualCertificates.size() + " manual certificate(s)");
    }

    /**
     * Parses certificate(s) from an input stream.
     * Supports multiple formats: PEM, DER, PKCS#7 (.p7b, .p7c)
     * Returns a list because PKCS#7 can contain multiple certificates.
     */
    private List<X509Certificate> parseCertificatesFromStream(InputStream is) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");

        // Mark the stream to allow reset if needed
        if (!is.markSupported()) {
            is = new BufferedInputStream(is);
        }
        is.mark(Integer.MAX_VALUE);

        try {
            // Try 1: Parse as standard X.509 (works for DER and single PEM)
            Certificate cert = cf.generateCertificate(is);
            if (cert instanceof X509Certificate) {
                certificates.add((X509Certificate) cert);

                // Check if there are more certificates (PEM can have multiple)
                try {
                    while (is.available() > 0) {
                        Certificate nextCert = cf.generateCertificate(is);
                        if (nextCert instanceof X509Certificate) {
                            certificates.add((X509Certificate) nextCert);
                        }
                    }
                } catch (Exception e) {
                    // No more certificates
                }

                if (!certificates.isEmpty()) {
                    return certificates;
                }
            }
        } catch (CertificateException e) {
            // Direct parsing failed, try other formats
            log.debug("Direct certificate parsing failed, trying other formats...");
        }

        // Try 2: Parse as PKCS#7 bundle (works for .p7b, .p7c, .spc)
        try {
            is.reset();
            Collection<? extends Certificate> certs = cf.generateCertificates(is);
            for (Certificate cert : certs) {
                if (cert instanceof X509Certificate) {
                    certificates.add((X509Certificate) cert);
                }
            }
            if (!certificates.isEmpty()) {
                log.info("Parsed " + certificates.size() + " certificate(s) from PKCS#7 bundle");
                return certificates;
            }
        } catch (Exception e) {
            log.debug("PKCS#7 parsing failed, trying PEM format...");
        }

        // Try 3: Parse as PEM format (text-based)
        try {
            is.reset();
            certificates = parsePEMCertificates(is);
            if (!certificates.isEmpty()) {
                return certificates;
            }
        } catch (Exception e) {
            log.debug("PEM parsing failed", e);
        }

        // If all parsing methods failed
        if (certificates.isEmpty()) {
            throw new CertificateException("Unable to parse certificate - unsupported format or corrupted file");
        }

        return certificates;
    }

    /**
     * Parses PEM-encoded certificates (supports multiple certificates in one file).
     */
    private List<X509Certificate> parsePEMCertificates(InputStream is) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder currentCert = new StringBuilder();
        String line;
        boolean inCert = false;

        while ((line = reader.readLine()) != null) {
            if (line.contains("BEGIN CERTIFICATE")) {
                inCert = true;
                currentCert = new StringBuilder();
                continue;
            }
            if (line.contains("END CERTIFICATE")) {
                if (currentCert.length() > 0) {
                    // Decode and parse this certificate
                    try {
                        byte[] decoded = Base64.getDecoder().decode(currentCert.toString());
                        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                            new ByteArrayInputStream(decoded));
                        certificates.add(cert);
                    } catch (Exception e) {
                        log.warn("Failed to parse PEM certificate block", e);
                    }
                }
                inCert = false;
                currentCert = new StringBuilder();
                continue;
            }
            if (inCert) {
                currentCert.append(line.trim());
            }
        }

        if (certificates.isEmpty()) {
            throw new CertificateException("No valid PEM certificate found");
        }

        log.info("Parsed " + certificates.size() + " certificate(s) from PEM format");
        return certificates;
    }

    /**
     * Parses a certificate from an input stream (backward compatibility).
     * Returns the first certificate if multiple are found.
     */
    private X509Certificate parseCertificate(InputStream is) throws Exception {
        List<X509Certificate> certs = parseCertificatesFromStream(is);
        if (certs.isEmpty()) {
            throw new CertificateException("No certificate found");
        }
        return certs.get(0);
    }

    /**
     * Parses certificate(s) from a file.
     * Returns a list because some formats (PKCS#7, multi-PEM) can contain multiple certificates.
     */
    private List<X509Certificate> parseCertificatesFromFile(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            return parseCertificatesFromStream(fis);
        }
    }

    /**
     * Parses a single certificate from a file (backward compatibility).
     */
    private X509Certificate parseCertificateFile(File file) throws Exception {
        List<X509Certificate> certs = parseCertificatesFromFile(file);
        if (certs.isEmpty()) {
            throw new CertificateException("No certificate found in file");
        }
        return certs.get(0);
    }

    /**
     * Adds a manual trust certificate.
     * Supports all certificate formats: PEM, DER, CER, CRT, P7B, P7C, SPC
     * If file contains multiple certificates, all will be added.
     *
     * @param certFile Certificate file to add
     * @param alias    Alias/name for the certificate
     * @throws Exception if certificate cannot be added
     */
    public synchronized void addTrustCertificate(File certFile, String alias) throws Exception {
        // Parse certificate(s) to validate
        List<X509Certificate> certs = parseCertificatesFromFile(certFile);

        if (certs.isEmpty()) {
            throw new CertificateException("No valid certificates found in file");
        }

        // Determine filename
        String filename = alias;
        String lowerFilename = filename.toLowerCase();
        if (!lowerFilename.endsWith(".pem") && !lowerFilename.endsWith(".der") &&
                !lowerFilename.endsWith(".cer") && !lowerFilename.endsWith(".crt") &&
                !lowerFilename.endsWith(".p7b") && !lowerFilename.endsWith(".p7c") &&
                !lowerFilename.endsWith(".spc")) {
            // Add extension based on original file
            String originalExt = getFileExtension(certFile.getName());
            filename = filename + "." + originalExt;
        }

        // Copy to user certificates directory
        File destFile = new File(USER_CERTS_DIR, filename);
        Files.copy(certFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Add certificate(s) to manual certificates map
        if (certs.size() == 1) {
            // Single certificate
            manualCertificates.put(filename, certs.get(0));
            log.info("Added manual trust certificate: " + filename + " - " + certs.get(0).getSubjectDN());
        } else {
            // Multiple certificates (PKCS#7 bundle or multi-PEM)
            for (int i = 0; i < certs.size(); i++) {
                String indexedAlias = filename + "[" + i + "]";
                manualCertificates.put(indexedAlias, certs.get(i));
                log.info("Added manual trust certificate: " + indexedAlias + " - " + certs.get(i).getSubjectDN());
            }
        }

        log.info("Successfully added " + certs.size() + " certificate(s) from " + certFile.getName());
    }

    /**
     * Removes a manual trust certificate.
     *
     * @param alias Alias of certificate to remove
     * @return true if removed, false if not found
     */
    public synchronized boolean removeTrustCertificate(String alias) {
        if (!manualCertificates.containsKey(alias)) {
            return false;
        }

        // Remove file
        File certFile = new File(USER_CERTS_DIR, alias);
        if (certFile.exists()) {
            certFile.delete();
        }

        // Remove from map
        manualCertificates.remove(alias);

        log.info("Removed manual trust certificate: " + alias);
        return true;
    }

    /**
     * Gets all trust anchors from configured sources (embedded + manual).
     * IMPORTANT: This method now returns ONLY root certificates (self-signed) as trust anchors.
     * Intermediate certificates are filtered out and should be obtained via getIntermediateCertificates().
     *
     * For proper certificate validation:
     * - Use this method for PKIXParameters.setTrustAnchors()
     * - Use getIntermediateCertificates() for PKIXParameters.addCertStore()
     *
     * @return Set of trust anchors (only root certificates)
     */
    public Set<TrustAnchor> getAllTrustAnchors() {
        // Delegate to getRootTrustAnchors() for correct behavior
        return getRootTrustAnchors();
    }

    /**
     * Checks if a certificate is a root certificate (self-signed).
     * Root certificates have subject == issuer.
     *
     * @param cert Certificate to check
     * @return true if root certificate, false if intermediate
     */
    private boolean isRootCertificate(X509Certificate cert) {
        // Root certificate is self-signed: subject DN equals issuer DN
        // Use CANONICAL format for proper normalized comparison
        try {
            String subjectDN = cert.getSubjectX500Principal().getName(javax.security.auth.x500.X500Principal.CANONICAL);
            String issuerDN = cert.getIssuerX500Principal().getName(javax.security.auth.x500.X500Principal.CANONICAL);
            return subjectDN.equals(issuerDN);
        } catch (Exception e) {
            log.warn("Error comparing DNs in canonical form, falling back to direct comparison", e);
            // Fallback to direct comparison
            return cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal());
        }
    }

    /**
     * Gets ONLY root trust anchors from configured sources (embedded + manual).
     * This method filters out intermediate certificates and returns only self-signed root certificates.
     * Use this for PKIXParameters.setTrustAnchors()
     *
     * @return Set of trust anchors (only root certificates)
     */
    public Set<TrustAnchor> getRootTrustAnchors() {
        if (!initialized) {
            initialize();
        }

        Set<TrustAnchor> rootAnchors = new HashSet<>();

        // Add only root certificates from embedded certificates
        for (X509Certificate cert : embeddedCertificates) {
            if (isRootCertificate(cert)) {
                rootAnchors.add(new TrustAnchor(cert, null));
                log.debug("Root certificate: " + cert.getSubjectDN().toString());
            }
        }

        // Add only root certificates from manual certificates
        for (X509Certificate cert : manualCertificates.values()) {
            if (isRootCertificate(cert)) {
                rootAnchors.add(new TrustAnchor(cert, null));
                log.debug("Root certificate (manual): " + cert.getSubjectDN().toString());
            }
        }

        log.info("Root trust anchors: " + rootAnchors.size() +
                " (filtered from " + (embeddedCertificates.size() + manualCertificates.size()) +
                " total certificates)");

        return rootAnchors;
    }

    /**
     * Gets ONLY intermediate certificates from configured sources (embedded + manual).
     * These certificates are needed for certificate path/chain building.
     * Use this with PKIXParameters.addCertStore() for proper chain validation.
     *
     * @return Collection of intermediate certificates (non-root certificates)
     */
    public Collection<X509Certificate> getIntermediateCertificates() {
        if (!initialized) {
            initialize();
        }

        List<X509Certificate> intermediates = new ArrayList<>();

        // Add only intermediate certificates from embedded certificates
        for (X509Certificate cert : embeddedCertificates) {
            if (!isRootCertificate(cert)) {
                intermediates.add(cert);
                log.debug("Intermediate certificate (embedded): " + cert.getSubjectDN().toString());
            }
        }

        // Add only intermediate certificates from manual certificates
        for (X509Certificate cert : manualCertificates.values()) {
            if (!isRootCertificate(cert)) {
                intermediates.add(cert);
                log.debug("Intermediate certificate (manual): " + cert.getSubjectDN().toString());
            }
        }

        log.info("Intermediate certificates: " + intermediates.size() +
                " (filtered from " + (embeddedCertificates.size() + manualCertificates.size()) +
                " total certificates)");

        return intermediates;
    }

    /**
     * Gets list of manual certificates (for UI display).
     */
    public Map<String, X509Certificate> getManualCertificates() {
        if (!initialized) {
            initialize();
        }
        return new LinkedHashMap<>(manualCertificates);
    }

    /**
     * Gets set of embedded certificates (for UI display).
     * Returns a copy to prevent modification.
     */
    public Set<X509Certificate> getEmbeddedCertificates() {
        if (!initialized) {
            initialize();
        }
        return new HashSet<>(embeddedCertificates);
    }

    /**
     * Gets count of embedded certificates.
     */
    public int getEmbeddedCertificateCount() {
        if (!initialized) {
            initialize();
        }
        return embeddedCertificates.size();
    }

    /**
     * Gets count of manual certificates.
     */
    public int getManualCertificateCount() {
        if (!initialized) {
            initialize();
        }
        return manualCertificates.size();
    }

    /**
     * Reloads all certificates.
     */
    public synchronized void reload() {
        initialized = false;
        initialize();
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "pem"; // default
    }
}
