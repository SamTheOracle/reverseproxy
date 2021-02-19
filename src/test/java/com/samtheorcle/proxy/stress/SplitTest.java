package com.samtheorcle.proxy.stress;

public class SplitTest {
    public static void main(String[] args) {
        String url = "http://localhost/api/v1/v2/telegram/isgay";
        String[] uri = url.split("/api/v1/v2");
        String whatIsThis = uri[0];
        String root = uri[1].split("/")[1];
        System.out.println(root);
    }
}
