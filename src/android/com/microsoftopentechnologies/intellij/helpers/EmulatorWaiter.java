/**
 * Copyright 2014 Microsoft Open Technologies Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoftopentechnologies.intellij.helpers;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class EmulatorWaiter {

    public static void main(String[] args) {

        final String adbPath = args[0];

        JPanel jPanel = new JPanel();
        jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));
        jPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel jLabel = new JLabel("Waiting emulator to start running...");
        jLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        jPanel.add(jLabel);

        JProgressBar jProgressBar = new JProgressBar();
        jProgressBar.setIndeterminate(true);
        jPanel.add(jProgressBar);

        Box horizontalBox = Box.createHorizontalBox();
        JButton closeButton = new JButton("Stop");
        horizontalBox.add(Box.createHorizontalGlue());
        horizontalBox.add(closeButton);
        jPanel.add(horizontalBox);

        final JDialog waitDialog = new JDialog();
        waitDialog.setUndecorated(true);
        waitDialog.setContentPane(jPanel);
        waitDialog.setResizable(false);
        waitDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        waitDialog.pack();
        waitDialog.setLocation(
                (Toolkit.getDefaultToolkit().getScreenSize().width) / 2 - waitDialog.getWidth() / 2,
                (Toolkit.getDefaultToolkit().getScreenSize().height) / 2 - waitDialog.getHeight() / 2);

        final AtomicBoolean isWindowsClosing = new AtomicBoolean(false);

        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                isWindowsClosing.set(true);

                waitDialog.setVisible(false);
                waitDialog.dispose();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waitAdb(adbPath, isWindowsClosing);

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {

                            waitDialog.setVisible(false);
                            waitDialog.dispose();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        waitDialog.setVisible(true);

    }

    private static void waitAdb(String adbPath, AtomicBoolean isWindowClosing) throws Exception {

        while(!isWindowClosing.get()) {


            Thread.sleep(1000);

            String[] listDevicesCmd = {
                    adbPath,
                    "devices"
            };

            File adb = new File(adbPath).getParentFile();
            String devices = runCommand(listDevicesCmd, adb);
            ArrayList<String> deviceList = new ArrayList<String>();

            for(String line : devices.split(Pattern.quote(System.getProperty("line.separator")))) {
                String[] lineParts = line.split(Pattern.quote("\t"));

                if (lineParts.length > 1 && "device".equals(lineParts[1])) {
                    deviceList.add(lineParts[0]);
                }
            }

            for(String device : deviceList) {
                String[] cmdIsVSEmu = {
                    adbPath,
                    "-s",
                    device,
                    "shell",
                    "getprop",
                    "ro.product.manufacturer"
                };

                if(runCommand(cmdIsVSEmu, adb).startsWith("VS Emulator")) {

                    String[] cmdFinishBoot = {
                        adbPath,
                        "-s",
                        device,
                        "shell",
                        "getprop",
                        "dev.bootcomplete"
                    };

                    if (runCommand(cmdFinishBoot, adb).startsWith("1")) {
                        isWindowClosing.set(true);
                    }
                }
            }
        }
    }

    private static String runCommand(String[] cmd, File path) throws Exception {
        final Process p;

        Runtime runtime = Runtime.getRuntime();
        p = runtime.exec(
                cmd,
                null, //new String[] {"PRECOMPILE_STREAMLINE_FILES=1"},
                path);

        String errResponse = new String(adaptiveLoadBytes(p.getErrorStream()));
        String content = new String(adaptiveLoadBytes(p.getInputStream()));
        if (p.waitFor() != 0) {
            Exception ex = new Exception(errResponse);
            ex.printStackTrace();

            throw ex;
        }

        return content;
    }

    private static byte[] adaptiveLoadBytes(InputStream stream) throws IOException {
        byte[] bytes = new byte[4096];
        List<byte[]> buffers = null;
        int count = 0;
        int total = 0;
        while (true) {
            int n = stream.read(bytes, count, bytes.length - count);
            if (n <= 0) break;
            count += n;
            if (total > 1024 * 1024 * 10) throw new IOException("File too big " + stream);
            total += n;
            if (count == bytes.length) {
                if (buffers == null) {
                    buffers = new ArrayList<byte[]>();
                }
                buffers.add(bytes);
                int newLength = Math.min(1024 * 1024, bytes.length * 2);
                bytes = new byte[newLength];
                count = 0;
            }
        }
        byte[] result = new byte[total];
        if (buffers != null) {
            for (byte[] buffer : buffers) {
                System.arraycopy(buffer, 0, result, result.length - total, buffer.length);
                total -= buffer.length;
            }
        }
        System.arraycopy(bytes, 0, result, result.length - total, total);
        return result;
    }

}
