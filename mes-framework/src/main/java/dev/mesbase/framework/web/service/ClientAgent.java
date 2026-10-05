package dev.mesbase.framework.web.service;
/** Bounded Source login metadata PORT; no geolocation call or untrusted identity. */
public record ClientAgent(String browser,String os) {
 public static ClientAgent parse(String raw){String ua=raw==null?"":raw.substring(0,Math.min(raw.length(),512));String browser=ua.contains("Edg/")?"Edge":ua.contains("Firefox/")?"Firefox":ua.contains("Chrome/")||ua.contains("CriOS/")?"Chrome":ua.contains("Safari/")?"Safari":"Unknown";String os=ua.contains("Android")?"Android":ua.contains("iPhone")||ua.contains("iPad")?"iOS":ua.contains("Windows NT")?"Windows":ua.contains("Mac OS X")?"macOS":ua.contains("Linux")?"Linux":"Unknown";return new ClientAgent(browser,os);}
}
