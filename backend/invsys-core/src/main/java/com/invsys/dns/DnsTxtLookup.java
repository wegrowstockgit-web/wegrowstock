package com.invsys.dns;

import java.util.List;

/**
 * Performs authoritative DNS TXT lookups for custom-domain verification.
 */
public interface DnsTxtLookup {
    List<String> lookupTxt(String domainName) throws Exception;
}
