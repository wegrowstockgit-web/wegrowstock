package com.invsys.dns;

import org.springframework.stereotype.Component;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Component
public class JndiDnsTxtLookup implements DnsTxtLookup {

    @Override
    public List<String> lookupTxt(String domainName) throws Exception {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        DirContext ctx = new InitialDirContext(env);
        try {
            Attributes attrs = ctx.getAttributes(domainName, new String[]{"TXT"});
            Attribute txt = attrs.get("TXT");
            if (txt == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            NamingEnumeration<?> all = txt.getAll();
            while (all.hasMore()) {
                Object value = all.next();
                if (value != null) {
                    values.add(unwrapTxt(value.toString()));
                }
            }
            return values;
        } finally {
            ctx.close();
        }
    }

    static String unwrapTxt(String raw) {
        String s = raw.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\" \"", "");
    }
}
