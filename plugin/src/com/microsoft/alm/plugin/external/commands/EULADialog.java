// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.external.commands;

import com.intellij.openapi.util.SystemInfo;
import com.microsoft.alm.helpers.Path;
import com.microsoft.alm.plugin.external.tools.TfTool;
import com.microsoft.alm.plugin.external.utils.ProcessHelper;
import kotlin.text.Charsets;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;
import java.util.jar.JarFile;

public class EULADialog {
    private final static org.slf4j.Logger logger = LoggerFactory.getLogger(EULADialog.class);
    static volatile boolean wasShown = false;
    private static final Object myLock = new Object();
    private static boolean isEULAAccepted = false;

    @Nullable
    private List<String> getEULAText(){
        try {
            String tfLocation = TfTool.getLocation();
            if (tfLocation == null) {
                return null;
            }
            List<String> result = new ArrayList<String>();
            String path = Path.combine(Path.combine(Path.getDirectoryName(tfLocation), "lib"), "com.microsoft.tfs.client.common.jar");
            JarFile jar = new JarFile(path);
            InputStream stream = jar.getInputStream(jar.getEntry("eula.txt"));
            BufferedReader rdr = new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8));
            String line;
            while ((line = rdr.readLine()) != null) {
                result.add(line);
            }
            return result;
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    private boolean acceptEula() {
        try {
            String tfLocation = TfTool.getLocation();
            Process process = ProcessHelper.startProcess(
                    Path.getDirectoryName(tfLocation), Arrays.asList(tfLocation, "eula", SystemInfo.isWindows ? "/accept" : "-accept"));
            process.waitFor();
            return true;
        } catch (Exception e) {
            logger.error("Can't accept EULA", e);
            return false;
        }
    }

    private JScrollPane createEULAPane(List<String> eulaLines) {
        JTextArea textArea = new JTextArea();
        int textWidth = 500;
        StringBuilder eulaText = new StringBuilder();
        FontMetrics fontMetrics = textArea.getFontMetrics(textArea.getFont());
        for (String line : eulaLines) {
            int width = SwingUtilities.computeStringWidth(fontMetrics, line);
            if (width > textWidth) {
                textWidth = width;
            }
            eulaText.append(line).append("\n");
        }
        textArea.setText(eulaText.toString());
        textArea.setMinimumSize(null);
        textArea.setBackground(null);
        textArea.setEditable(false);

        @SuppressWarnings("UndesirableClassUsage")
        JScrollPane scrollPane = new JScrollPane(textArea);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        scrollPane.setPreferredSize(new Dimension(textWidth, 500));
        return scrollPane;
    }

    public boolean show() {
        synchronized (myLock) {
            if (wasShown) {
                return isEULAAccepted;
            }
            List<String> eulaLines = getEULAText();
            if (eulaLines == null) {
                isEULAAccepted = false;
                wasShown = true;
                return false;
            }

            String[] options = { "Accept", "Decline" };
            boolean accepted = JOptionPane.showOptionDialog(null, createEULAPane(eulaLines),
                    "Team Foundation Command-line Client EULA",
                    JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[1]) == 0;
            if (accepted) {
                isEULAAccepted = acceptEula();
                wasShown = true;
                return isEULAAccepted;
            } else {
                wasShown = true;
                return false;
            }
        }
    }
}
