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

package com.microsoftopentechnologies.intellij.actions;

import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.tools.Tool;
import com.intellij.tools.ToolAction;
import com.intellij.tools.ToolManager;
import com.intellij.tools.ToolsGroup;
import com.microsoftopentechnologies.intellij.forms.VisualStudioEmulatorManagerForm;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class VisualStudioAndroidEmulatorManagerAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        String mOsVersion = System.getProperty("os.name");
        String osName = mOsVersion.split(" ")[0];

        e.getPresentation().setVisible(osName.equals("Windows"));
    }

    public void actionPerformed(AnActionEvent e) {

        VisualStudioEmulatorManagerForm form = new VisualStudioEmulatorManagerForm(e.getProject());
        form.show();

    }
}
