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

package com.microsoftopentechnologies.intellij.forms;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.microsoftopentechnologies.intellij.helpers.VSEmulatorHelper;
import com.microsoftopentechnologies.tooling.msservices.components.DefaultLoader;
import com.microsoftopentechnologies.tooling.msservices.helpers.XmlHelper;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.xml.xpath.XPathConstants;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;


public class VisualStudioEmulatorManagerForm extends DialogWrapper {

    private JCheckBox useVisualStudioEmulatorCheckBox;
    private JList imagesList;
    private JButton manageImagesButton;
    private JPanel mainPanel;

    private Project project;
    private File[] files;

    public VisualStudioEmulatorManagerForm(@Nullable Project project) {
        super(project, true);
        init();

        this.project = project;

        this.setTitle("Visual Studio Emulator for Android");

        useVisualStudioEmulatorCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                imagesList.setEnabled(useVisualStudioEmulatorCheckBox.isSelected());
                manageImagesButton.setEnabled(useVisualStudioEmulatorCheckBox.isSelected());
            }
        });

        manageImagesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                VSEmulatorHelper.showImagesManager();
            }
        });

        fillList();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return mainPanel;
    }

    @Override
    protected void doOKAction() {
        try {
            if (project.getWorkspaceFile() != null) {
                String xml = new String(project.getWorkspaceFile().contentsToByteArray());
                NodeList nodeList = (NodeList) XmlHelper.getXMLValue(xml,
                        "/project/component[@name='RunManager']/configuration[@type='AndroidRunConfigurationType'][@default='false']/method",
                        XPathConstants.NODESET);

                Node item = nodeList.item(0);

                for (int i = 0; i < item.getChildNodes().getLength(); i++) {
                    Node node = item.getChildNodes().item(i);
                    if (node.hasAttributes()) {
                        String value = XmlHelper.getAttributeValue(node, "actionId");
                        if (value != null && value.equals("Tool_External Tools_RunVSEmu")) {
                            item.removeChild(node);
                        }
                    }
                }

                if (useVisualStudioEmulatorCheckBox.isSelected()) {
                    Element option = item.getOwnerDocument().createElement("option");
                    option.setAttribute("name", "ToolBeforeRunTask");
                    option.setAttribute("enabled", "true");
                    option.setAttribute("actionId", "Tool_External Tools_RunVSEmu");

                    if(item.hasChildNodes()) {
                        item.insertBefore(option, item.getFirstChild());
                    } else {
                        item.appendChild(option);
                    }

                    VSEmulatorHelper.setVSEmuTool(files[imagesList.getSelectedIndex()]);
                } else {
                    VSEmulatorHelper.unsetVSEmuTool();
                }

                final String content = XmlHelper.saveXmlToStreamWriter(nodeList.item(0).getOwnerDocument());

                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            project.getWorkspaceFile().setBinaryContent(content.getBytes());
                        } catch (Throwable ex) {
                            DefaultLoader.getUIHelper().showException(
                                    "Error trying to change run settings",
                                    ex,
                                    "Visual Studio Emulator for Android",
                                    false,
                                    true);
                        }
                    }
                });

                this.close(DialogWrapper.OK_EXIT_CODE);
            }
        } catch (Throwable ex) {
            DefaultLoader.getUIHelper().showException(
                    "Error trying to change run settings",
                    ex,
                    "Visual Studio Emulator for Android",
                    false,
                    true);
        }

    }

    private void fillList() {
        files = VSEmulatorHelper.getEmulatorList();

        imagesList.setModel(new AbstractListModel() {
            @Override
            public int getSize() {
                return files.length;
            }

            @Override
            public Object getElementAt(int i) {
                return files[i].getName().replace(".cfg", "");
            }
        });

        try {
            File configFile = VSEmulatorHelper.getVSEmuToolName();

            if(configFile != null) {
                for (int i = 0; i < files.length; i++) {
                    if(configFile.getName().equals(files[i].getName())) {
                        imagesList.setEnabled(true);
                        manageImagesButton.setEnabled(true);

                        useVisualStudioEmulatorCheckBox.setSelected(true);
                        imagesList.setSelectedIndex(i);
                    }
                }
            }

        } catch (IOException ex) {
            DefaultLoader.getUIHelper().showException(
                    "Error trying to change run settings",
                    ex,
                    "Visual Studio Emulator for Android",
                    false,
                    true);
        }
    }
}
