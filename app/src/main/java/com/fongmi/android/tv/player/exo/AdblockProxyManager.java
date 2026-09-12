package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

public class AdblockProxyManager {

    private static volatile AdblockProxy proxy;

    public static synchronized String start(String url) {
        if (proxy == null) {
            try {
                proxy = new AdblockProxy(0);
                proxy.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            } catch (IOException e) {
                proxy = null;
                return null;
            }
        }
        return "http://127.0.0.1:" + proxy.getListeningPort()
                + "/?url=" + Uri.encode(url);
    }
}