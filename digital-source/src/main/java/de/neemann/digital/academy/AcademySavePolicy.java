/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.gui.Main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Denies saving circuits into the project resources and into the original Digital installation.
 * Paths are compared after resolving links and case, including all sub directories.
 */
public final class AcademySavePolicy implements Main.FileSavePolicy {
    private final List<String> roots = new ArrayList<>();

    /**
     * Creates the policy protecting the given directories.
     *
     * @param protectedDirs the directories
     */
    public AcademySavePolicy(List<File> protectedDirs) {
        for (File d : protectedDirs)
            try {
                roots.add(real(d));
            } catch (IOException e) {
                roots.add(d.getAbsolutePath().toLowerCase(Locale.ROOT));
            }
    }

    /**
     * Creates the policy for the project.
     *
     * @param projectRoot the project root
     * @return the policy
     */
    public static AcademySavePolicy forProject(File projectRoot) {
        List<File> dirs = new ArrayList<>();
        for (String d : new String[]{"levels", "digital-source", "build-baseline", "tools", "assets", "downloads"})
            dirs.add(new File(projectRoot, d));
        String profile = System.getenv("USERPROFILE");
        if (profile != null)
            dirs.add(new File(profile, "Desktop" + File.separator + "Digital"));
        String extra = System.getProperty("academy.protect");
        if (extra != null)
            for (String e : extra.split(File.pathSeparator))
                if (!e.trim().isEmpty())
                    dirs.add(new File(e.trim()));
        return new AcademySavePolicy(dirs);
    }

    @Override
    public String checkSave(File file) {
        String p;
        try {
            p = real(file);
        } catch (IOException e) {
            return "无法确认保存位置：" + e.getMessage();
        }
        for (String r : roots)
            if (p.equals(r) || p.startsWith(r + File.separator))
                return "这是受保护的项目资源或原版 Digital 目录，不能保存到：" + file
                        + "\n请另存到 work" + File.separator + "sandbox 或其他普通位置。";
        return null;
    }

    /**
     * Resolves the real path of a possibly not yet existing file.
     */
    static String real(File f) throws IOException {
        Path p = f.toPath().toAbsolutePath().normalize();
        Deque<String> rest = new ArrayDeque<>();
        Path existing = p;
        while (existing != null && !Files.exists(existing)) {
            if (existing.getFileName() != null)
                rest.push(existing.getFileName().toString());
            existing = existing.getParent();
        }
        Path r = existing == null ? p : existing.toRealPath();
        if (existing != null)
            while (!rest.isEmpty())
                r = r.resolve(rest.pop());
        return r.normalize().toString().toLowerCase(Locale.ROOT);
    }
}
