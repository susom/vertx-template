/*
 * Copyright 2016 The Board of Trustees of The Leland Stanford Junior University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.susom.app.server.container;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.jar.Manifest;

/**
 * This is the main entry point for the application, but is mostly
 * boilerplate for configuring and launching things.
 */
public class Main {
  public static void main(String[] args) {
    try {
      String myLocation = Main.class.getProtectionDomain().getCodeSource().getLocation().toString();
      if (myLocation.endsWith(".jar")) {
        Manifest manifest;
        try (InputStream in = new URL("jar:" + myLocation + "!/META-INF/MANIFEST.MF").openStream()) {
          manifest = new Manifest(in);
        }
        String appJar = manifest.getMainAttributes().getValue("App-Jar");

        if (appJar == null) {
          System.out.println("Launching in fatjar mode");

          URLClassLoader cl = (URLClassLoader) Main.class.getClassLoader();
          Thread.currentThread().setContextClassLoader(cl);
          new SecurityPolicy(true).install();
          new Server().launch(new String[0]);
          return;
        }

        // This is an experimental work in progress where we package as a set of embedded
        // jars so we can better control various security manager policies
        System.out.println("Launching in specialjar mode");

        URL appJarUrl;
        try (InputStream in = new URL("jar:" + myLocation + "!/" + appJar).openStream()) {
          appJarUrl = extractJarToTempDir(in);
        }

        // This is to work-around problems with dynamically calling Policy.setPolicy()
        // as part of enabling SecurityManager sandboxing. If you load the application
        // as a fat jar, the class loader starting Main will cache the current policy
        // (none) when it initializes, which effectively disables security.
        URLClassLoader system = (URLClassLoader) ClassLoader.getSystemClassLoader();
//        URLClassLoader boot = (URLClassLoader) system.getParent();
//        System.out.println("System: " + Arrays.asList(system.getURLs()));
//        System.out.println("Boot: " + Arrays.asList(boot.getURLs()));

        ClassLoader serverClassLoader = new URLClassLoader(new URL[] { appJarUrl }, system);
        ClassLoader client = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(serverClassLoader);
        Class<?> serverClass = serverClassLoader.loadClass("com.github.susom.app.server.container.SecurityPolicy");

        Object policy = serverClass.getConstructor(boolean.class).newInstance(false);
        serverClass.getMethod("install").invoke(policy);
        Thread.currentThread().setContextClassLoader(client);

        serverClassLoader = new URLClassLoader(new URL[] { appJarUrl }, system);
        Thread.currentThread().setContextClassLoader(serverClassLoader);
        serverClass = serverClassLoader.loadClass("com.github.susom.app.server.container.Server");
        Object server = serverClass.getConstructor(boolean.class).newInstance(false);
        serverClass.getMethod("launch").invoke(server, new Object[] { new String[0] });
        Thread.currentThread().setContextClassLoader(client);
      } else {
        System.out.println("Launching in IDE mode");

        new SecurityPolicy(true).install();
        new Server().launch(new String[0]);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static URL extractJarToTempDir(InputStream jarStream) throws Exception {
    File jar = Files.createTempFile("unpacked-app-", ".jar").toFile();
    jar.deleteOnExit();
    try (FileOutputStream out = new FileOutputStream(jar)) {
      int bytesRead;
      byte[] buf = new byte[4096];
      while ((bytesRead = jarStream.read(buf)) != -1) {
        out.write(buf, 0, bytesRead);
      }
    }
    return jar.toURI().toURL();
  }
}
