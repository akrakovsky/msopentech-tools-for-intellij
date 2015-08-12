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
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.project.Project;
import com.intellij.tools.Tool;
import com.intellij.tools.ToolManager;
import com.intellij.tools.ToolsGroup;
import com.microsoftopentechnologies.tooling.msservices.components.DefaultLoader;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureCmdException;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

public class VSEmulatorHelper {
    private static final String IMAGE_STORAGE = System.getenv("LOCALAPPDATA") + "\\Microsoft\\VisualStudioEmulator\\Android\\Containers\\Local\\Devices\\";
    private static final String RUN_CMD_FORMAT = "/c start /B %s /sku Android  /displayName \"%s\" /memSize %s /diagonalSize %s /video \"%s\" /vhd \"%s\" /name \"%s\"";

    private static final String GROUP_NAME = "External Tools";
    private static final String XDE_TOOL_NAME = "RunVSEmu";
    private static final String ADB_TOOL_NAME = "RunAdbDevice";

    public static void unsetVSEmuTool() {
        List<ToolsGroup<Tool>> groups = ToolManager.getInstance().getGroups();

        for (ToolsGroup<Tool> toolsGroup : groups) {
            if (toolsGroup.getName().equals(GROUP_NAME)) {
                for (Tool tool : toolsGroup.getElements()) {
                    if(tool.getName().equals(XDE_TOOL_NAME) || tool.getName().equals(ADB_TOOL_NAME)) {
                        toolsGroup.removeElement(tool);
                    }
                }
            }
        }

        ToolManager.getInstance().setTools(groups.toArray(new ToolsGroup[groups.size()]));
    }

    public static File getVSEmuToolName() throws IOException {

        Tool existingTool = getExistingTool(XDE_TOOL_NAME);
        if(existingTool != null) {

            String displayNameTool = null;

            CommandLineTokenizer commandLine = new CommandLineTokenizer(existingTool.getParameters());

            while(commandLine.hasMoreTokens()) {
                if(commandLine.nextToken().equals("/displayName")) {
                    displayNameTool = commandLine.peekNextToken();
                }
            }

            if (displayNameTool != null) {

                for (File configFile : getEmulatorList()) {
                    List<String> configData = Files.readLines(configFile, Charset.defaultCharset());
                    String displayName = getConfigValue(configData, "device.name").replace("\"", "");
                    if(displayName.equals(displayNameTool)) {
                        return configFile;
                    }
                }
            }
        }

        return null;
    }

    public static void setVSEmuTool(File configFile, Project project) throws IOException {
        Vector<ToolsGroup<Tool>> groups = new Vector<ToolsGroup<Tool>>(ToolManager.getInstance().getGroups());

        ToolsGroup<Tool> externalTools = null;

        for (ToolsGroup<Tool> toolsGroup : groups) {
            if(toolsGroup.getName().equals(GROUP_NAME)) {
                externalTools = toolsGroup;
            }
        }

        if(externalTools == null) {
            externalTools = new ToolsGroup<Tool>(GROUP_NAME);
            groups.add(externalTools);
        }

        externalTools.addElement(createXdeTool(configFile));

        File adb = AndroidSdkUtils.getAdb(project);
        if(adb != null) {
            externalTools.addElement(createAdbTool(adb.getPath()));
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
                String[] cmd = {workingDir.getPath() + File.separator + "emulatormgr", "/sku:Android"};

                runCommand(cmd, workingDir);
            } catch (Exception ex) {
                DefaultLoader.getUIHelper().showException("Error executing Visual Studio Emulator for Android", ex);
            }
        } else {
            DefaultLoader.getUIHelper().showException("Visual Studio Emulator for Android not found", new Exception());
        }

    }

    private static Tool createXdeTool(File configFile) throws IOException {
        List<String> configData = Files.readLines(configFile, Charset.defaultCharset());

        String displayName = getConfigValue(configData, "device.name").replace("\"", "");
        String memSize = getConfigValue(configData, "device.vm.ram.size");
        String diagonalSize = getConfigValue(configData, "device.screen.diagonal");
        String video = getConfigValue(configData, "device.screen.resolution");
        String vhd = configFile.getParentFile().getPath() + "\\" + getConfigValue(configData, "device.vm.vhd").replace("\\\\","\\");
        String name= getConfigValue(configData, "device.name").replace("\"", "") + "." + System.getProperty("user.name");

        String cmdParams = String.format(RUN_CMD_FORMAT, escapeCmdPath(getXDEPath() + "\\xde.exe"), displayName, memSize, diagonalSize, video, vhd, name);

        Tool tool = getExistingTool(XDE_TOOL_NAME);

        if(tool == null) {
            tool = new Tool();
        }

        try {

            Method setName = Tool.class.getDeclaredMethod("setName", String.class);
            setName.setAccessible(true);
            setName.invoke(tool, XDE_TOOL_NAME);

        } catch (Throwable ignore){}

        tool.setEnabled(true);
        tool.setProgram("cmd");

        tool.setParameters(cmdParams);
        tool.setWorkingDirectory(getXDEPath());

        return tool;
    }

    @NotNull
    private static Tool createAdbTool(String path) {
        Tool tool = getExistingTool(ADB_TOOL_NAME);
        if(tool == null) {
            tool = new Tool();
        }

        try {

            Method setName = Tool.class.getDeclaredMethod("setName", String.class);
            setName.setAccessible(true);
            setName.invoke(tool, ADB_TOOL_NAME);

        } catch (Throwable ignore){}

        tool.setEnabled(true);
        tool.setProgram("java");

        String className = EmulatorWaiter.class.getName();
        String classUrl = EmulatorWaiter.class.getResource(EmulatorWaiter.class.getSimpleName() + ".class").getPath();
        File classFile = new File(classUrl);
        if (classFile.exists()) {
            //remove package directories
            for (int i = 0; i < className.split(Pattern.quote(".")).length; i++) {
                classFile = classFile.getParentFile();
            }

            tool.setParameters(String.format("%s \"%s\"", className, path));
            tool.setWorkingDirectory(classFile.getPath());
        } else {
            String jarPath = classUrl.replace("jar:", "").split(Pattern.quote("!"))[0];

            tool.setParameters(String.format("-cp \"%s\" %s \"%s\"", jarPath, className, path));
            tool.setWorkingDirectory(System.getProperty("java.home"));
        }

        return tool;
    }

    private static Tool getExistingTool(String toolName) {
        for (Tool existingTool : ToolManager.getInstance().getTools(GROUP_NAME)) {
            if(existingTool.getName().equals(toolName)) {
                return existingTool;
            }
        }

        return null;
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


    @NotNull
    private static String getProgramFilesPath() {
        String path = System.getenv("programfiles") + " (x86)" ;

        //if windows is 32bit
        if(!new File(path).exists()) {
            path = System.getenv("programfiles");
        }

        return path;
    }

    @NotNull
    private static String getXDEPath() {
        File[] xdeDirectories = new File(getProgramFilesPath() + "\\Microsoft XDE\\").listFiles();
        if(xdeDirectories != null) {
            for (File file : xdeDirectories){
                if (file.getName().startsWith("10.")) {
                    return file.getPath();
                }
            }
        }

        return "";
    }

    private static String escapeCmdPath(String path) {
        List<String> newPath = new ArrayList<String>();
        String[] parts = path.split(Pattern.quote(File.separator));

        newPath.add(parts[0]);

        for (int i = 1; i < parts.length ; i++) {
            newPath.add("\"" + parts[i] + "\"");
        }


        return StringUtils.join(newPath, File.separator);
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
