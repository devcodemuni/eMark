package com.codemuni.core.keyStoresProvider;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public final class X509SubjectUtils {

    // Prevent instantiation
    private X509SubjectUtils() {
    }

    /**
     * Parse subject DN into a map of key-value pairs
     */
    private static Map<String, String> parseDN(String dn) {
        Map<String, String> result = new HashMap<>();
        try {
            LdapName ldapName = new LdapName(dn);
            for (Rdn rdn : ldapName.getRdns()) {
                result.put(rdn.getType().toUpperCase(), rdn.getValue().toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static Map<String, String> getSubjectMap(X509Certificate cert) {
        return parseDN(cert.getSubjectX500Principal().getName());
    }

    private static Map<String, String> getIssuerMap(X509Certificate cert) {
        return parseDN(cert.getIssuerX500Principal().getName());
    }

    public static String getFullSubjectDN(X509Certificate cert) {
        return cert.getSubjectDN().getName();
    }

    public static String getFullIssuerDN(X509Certificate cert) {
        return cert.getIssuerX500Principal().getName();
    }


    // Subject DN fields
    public static String getCommonName(X509Certificate cert) {
        return getSubjectMap(cert).get("CN");
    }

    public static String getOrganization(X509Certificate cert) {
        return getSubjectMap(cert).get("O");
    }

    public static String getOrganizationalUnit(X509Certificate cert) {
        return getSubjectMap(cert).get("OU");
    }

    public static String getCountry(X509Certificate cert) {
        return getSubjectMap(cert).get("C");
    }

    public static String getStateOrProvince(X509Certificate cert) {
        return getSubjectMap(cert).get("ST");
    }

    public static String getLocality(X509Certificate cert) {
        return getSubjectMap(cert).get("L");
    }

    public static String getEmailAddress(X509Certificate cert) {
        return getSubjectMap(cert).get("EMAILADDRESS");
    }

    public static String getSerialNumber(X509Certificate cert) {
        return getSubjectMap(cert).get("SERIALNUMBER");
    }

    // Issuer DN fields

    public static String getIssuerCommonName(X509Certificate cert) {
        return getIssuerMap(cert).get("CN");
    }

    public static String getIssuerOrganization(X509Certificate cert) {
        return getIssuerMap(cert).get("O");
    }

    public static String getIssuerOrganizationalUnit(X509Certificate cert) {
        return getIssuerMap(cert).get("OU");
    }

    public static String getIssuerCountry(X509Certificate cert) {
        return getIssuerMap(cert).get("C");
    }

    public static String getIssuerStateOrProvince(X509Certificate cert) {
        return getIssuerMap(cert).get("ST");
    }

    public static String getIssuerLocality(X509Certificate cert) {
        return getIssuerMap(cert).get("L");
    }

    public static String getIssuerEmailAddress(X509Certificate cert) {
        return getIssuerMap(cert).get("EMAILADDRESS");
    }

    public static String getIssuerSerialNumber(X509Certificate cert) {
        return getIssuerMap(cert).get("SERIALNUMBER");
    }
}
