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

import com.google.common.io.Files;
import com.intellij.tools.Tool;
import com.intellij.tools.ToolManager;
import com.intellij.tools.ToolsGroup;
import com.microsoftopentechnologies.tooling.msservices.components.DefaultLoader;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureCmdException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class VSEmulatorHelper {
    //TODO: Create config pane to manually change the path
    private static final String IMAGE_STORAGE = System.getenv("LOCALAPPDATA") + "\\Microsoft\\VisualStudioEmulator\\Android\\Containers\\Local\\Devices\\";
    private static final String RUN_CMD_FORMAT = "/c start /B %s /sku Android  /displayName \"%s\" /memSize %s /diagonalSize %s /video \"%s\" /vhd \"%s\" /name \"%s\"";
    private static final String XDE_PATH = getProgramFilesPath() + "\\Microsoft XDE\\10.0.1.0";

    //TODO: Build this path by code from XDE_PATH
    private static final String ESCAPED_XDE_PATH = "c:\\\"Program Files (x86)\"\\\"Microsoft XDE\"\\10.0.1.0\\xde.exe";

    public static void unsetVSEmuTool() {
        List<ToolsGroup<Tool>> groups = ToolManager.getInstance().getGroups();

        for (ToolsGroup<Tool> toolsGroup : groups) {
            if (toolsGroup.getName().equals("External Tools")) {
                for (Tool tool : toolsGroup.getElements()) {
                    if(tool.getName().equals("RunVSEmu")) {
                        toolsGroup.removeElement(tool);
                    }
                }
            }
        }

        ToolManager.getInstance().setTools(groups.toArray(new ToolsGroup[groups.size()]));
    }

    public static void setVSEmuTool(File configFile) throws IOException {

        List<String> configData = Files.readLines(configFile, Charset.defaultCharset());

        String displayName = getConfigValue(configData, "device.name").replace("\"", "");
        String memSize = getConfigValue(configData, "device.vm.ram.size");
        String diagonalSize = getConfigValue(configData, "device.screen.diagonal");
        String video = getConfigValue(configData, "device.screen.resolution");
        String vhd = configFile.getParentFile().getPath() + "\\" + getConfigValue(configData, "device.vm.vhd").replace("\\\\","\\");
        String name= getConfigValue(configData, "device.name").replace("\"","");

        String cmdParams = String.format(RUN_CMD_FORMAT, ESCAPED_XDE_PATH, displayName, memSize, diagonalSize, video, vhd, name);

        List<ToolsGroup<Tool>> groups = ToolManager.getInstance().getGroups();

        for (ToolsGroup<Tool> toolsGroup : groups) {
            if(toolsGroup.getName().equals("External Tools")) {
                Tool tool = new Tool();

                try {

                    Method setName = Tool.class.getDeclaredMethod("setName", String.class);
                    setName.setAccessible(true);
                    setName.invoke(tool, "RunVSEmu");

                } catch (Throwable ignore){}
                tool.setEnabled(true);
                tool.setProgram("cmd");

                tool.setParameters(cmdParams);
                tool.setWorkingDirectory(XDE_PATH);

                toolsGroup.addElement(tool);
            }
        }

        ToolManager.getInstance().setTools(groups.toArray(new ToolsGroup[groups.size()]));
    }

    public static File[] getEmulatorList() {
        File devFolder = new File(IMAGE_STORAGE);
        return devFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().endsWith(".cfg");
            }
        });
    }

    public static void showImagesManager() {

        final File workingDir = new File(
                getProgramFilesPath()
                + File.separator
                + "Microsoft Emulator Manager"
                + File.separator
                + "1.0");

        if(workingDir.exists()) {
            try {
                String[] cmd = {workingDir.getPath() + File.separator + "emulatormgr", "/sku", "Android"};

                runCommand(cmd, workingDir);
            } catch (Exception ex) {
                DefaultLoader.getUIHelper().showException("Error executing Visual Studio Emulator for Android", ex);
            }
        } else {
            DefaultLoader.getUIHelper().showException("Visual Studio Emulator for Android not found", new Exception());
        }

    }

    @NotNull
    private static String getConfigValue(List<String> configLines, String name) {
        for(String line : configLines) {
            if(name.equals(line.split("=")[0])) {
                return line.split("=")[1];
            }
        }

        return "";
    }


    private static String getProgramFilesPath() {
        String path = System.getenv("programfiles") + " (x86)" ;

        //if windows is 32bit
        if(!new File(path).exists()) {
            path = System.getenv("programfiles");
        }

        return path;
    }

    private static void runCommand(String[] cmd, File path) throws AzureCmdException, IOException, InterruptedException {
        final Process p;

        Runtime runtime = Runtime.getRuntime();
        p = runtime.exec(
                cmd,
                null, //new String[] {"PRECOMPILE_STREAMLINE_FILES=1"},
                path);

        String errResponse = new String(adaptiveLoadBytes(p.getErrorStream()));

        if (p.waitFor() != 0) {
            AzureCmdException ex = new AzureCmdException("Error executing Visual Studio Emulator for Android\n", errResponse);
            ex.printStackTrace();

            throw ex;
        }
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
