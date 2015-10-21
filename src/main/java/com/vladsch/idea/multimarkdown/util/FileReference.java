/*
 * Copyright (c) 2015-2015 Vladimir Schneider <vladimir.schneider@gmail.com>
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.vladsch.idea.multimarkdown.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.vladsch.idea.multimarkdown.MultiMarkdownPlugin;
import com.vladsch.idea.multimarkdown.psi.MultiMarkdownFile;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileReference extends FilePathInfo {
    private static final Logger logger = Logger.getLogger(FileReference.class);

    public interface ProjectFileResolver {
        VirtualFile getVirtualFile(@NotNull String sourcePath);
        PsiFile getPsiFile(@NotNull String sourcePath, @NotNull Project project);
    }

    public static ProjectFileResolver projectFileResolver = null;

    protected final Project project;

    public FileReference(@NotNull String filePath) {
        super(filePath);
        this.project = null;
    }

    public FileReference(@NotNull FilePathInfo filePath) {
        super(filePath);
        this.project = null;
    }

    public FileReference(@NotNull FilePathInfo filePath, Project project) {
        super(filePath);
        this.project = project;
    }

    public FileReference(@NotNull String filePath, Project project) {
        super(filePath);
        this.project = project;
    }

    public FileReference(@NotNull VirtualFile file, Project project) {
        super(file.getPath());
        this.project = project;
    }

    public FileReference(@NotNull PsiFile file) {
        super(file.getVirtualFile().getPath());
        this.project = file.getProject();
    }

    public FileReference(@NotNull FileReference other) {
        super(other);
        this.project = other.project;
    }
    @Nullable
    @Override
    public FileReference resolveLinkRef(@Nullable String linkRef, boolean convertGitHubWikiHome) {
        return resolveLinkRef(linkRef, convertGitHubWikiHome, false);
    }
    @Nullable
    @Override
    public FileReference resolveLinkRefWithAnchor(@Nullable String linkRef, boolean convertGitHubWikiHome) {
        return resolveLinkRef(linkRef, convertGitHubWikiHome, true);
    }
    @Nullable
    @Override
    public FileReference resolveLinkRef(@Nullable String linkRef) {
        return resolveLinkRef(linkRef, false, false);
    }

    @Nullable
    @Override
    public FileReference resolveLinkRefWithAnchor(@Nullable String linkRef) {
        return resolveLinkRef(linkRef, false, true);
    }

    @Nullable
    @Override
    public FileReference resolveLinkRefToWikiPage(@Nullable String linkRef) {
        return resolveLinkRef(linkRef, true, false);
    }

    @Nullable
    @Override
    public FileReference resolveLinkRefWithAnchorToWikiPage(@Nullable String linkRef) {
        return resolveLinkRef(linkRef, true, true);
    }

    protected class MarkdownGitHubLinkResolver extends FilePathInfo.LinkRefResolver {
        MarkdownGitHubLinkResolver(@NotNull String lastPart) {
            super("..", "..", lastPart);
        }

        @Override
        boolean isMatched(FilePathInfo currentPath, String[] linkRefParts, int part) {
            return project != null && !currentPath.isWikiHome() && super.isMatched(currentPath, linkRefParts, part);
        }

        @Override
        FilePathInfo computePath(FilePathInfo currentPath) {
            // if this is not a wiki home and what comes next is ../../{githubword} then we can replace is a subdirectory with current dir name with .wiki added
            assert project != null;

            GithubRepo githubRepo = MultiMarkdownPlugin.getProjectComponent(project).getGithubRepo(currentPath.getFullFilePath());
            if (githubRepo != null) {
                try {
                    String url = githubRepo.githubBaseUrl();
                    return new FilePathInfo(url).append(matchParts[matchParts.length - 1]);
                } catch (RuntimeException ignored) {
                    logger.info("Can't resolve GitHub url", ignored);
                }
            }
            return currentPath;
        }
    }

    protected class MarkdownGitHubWikiExternalLinkResolver extends FilePathInfo.LinkRefResolver {
        MarkdownGitHubWikiExternalLinkResolver(@NotNull String lastPart) {
            super("..", "..", lastPart);
        }

        @Override
        boolean isMatched(FilePathInfo currentPath, String[] linkRefParts, int part) {
            return project != null && !currentPath.isWikiHome() && super.isMatched(currentPath, linkRefParts, part);
        }

        @Override
        FilePathInfo computePath(FilePathInfo currentPath) {
            // if this is not a wiki home and what comes next is ../../wiki then we can replace is a subdirectory with current dir name with .wiki added
            assert project != null;

            FilePathInfo wikiPath = currentPath.append(currentPath.getFileName() + WIKI_HOME_EXTENTION);
            GithubRepo githubRepo = MultiMarkdownPlugin.getProjectComponent(project).getGithubRepo(wikiPath.getFullFilePath());
            if (githubRepo != null) {
                try {
                    String url = githubRepo.repoUrlFor("/");
                    return new FilePathInfo(url);
                } catch (RuntimeException ignored) {
                    logger.info("Can't resolve GitHub url", ignored);
                }
            }
            return currentPath;
        }
    }

    protected final MarkdownGitHubWikiExternalLinkResolver markdownGitHubWikiLinkResolver = new MarkdownGitHubWikiExternalLinkResolver(WIKI_HOME_NAME);
    protected final MarkdownGitHubLinkResolver markdownGitHubIssuesLinkResolver = new MarkdownGitHubLinkResolver(GITHUB_ISSUES_NAME);
    protected final MarkdownGitHubLinkResolver markdownGitHubPullsLinkResolver = new MarkdownGitHubLinkResolver(GITHUB_PULLS_NAME);
    protected final MarkdownGitHubLinkResolver markdownGitHubPulseLinkResolver = new MarkdownGitHubLinkResolver(GITHUB_PULSE_NAME);
    protected final MarkdownGitHubLinkResolver markdownGitHubGraphsLinkResolver = new MarkdownGitHubLinkResolver(GITHUB_GRAPHS_NAME);

    @Nullable
    @Override
    protected FileReference resolveLinkRef(@Nullable String linkRef, boolean convertLinkRefs, boolean withAnchor, LinkRefResolver... linkRefResolvers) {
        FilePathInfo resolvedPathInfo = super.resolveLinkRef(linkRef, convertLinkRefs, withAnchor, linkRefResolvers);
        return resolvedPathInfo != null ? new FileReference(resolvedPathInfo, this.project) : null;
    }

    @Nullable
    protected FileReference resolveExternalLinkRef(@Nullable String linkRef, boolean withAnchor, LinkRefResolver... linkRefResolvers) {
        FilePathInfo resolvedPathInfo = super.resolveLinkRef(linkRef, true, withAnchor, appendResolvers(linkRefResolvers, markdownGitHubIssuesLinkResolver, markdownGitHubWikiLinkResolver, markdownGitHubPullsLinkResolver, markdownGitHubPulseLinkResolver, markdownGitHubGraphsLinkResolver));
        return resolvedPathInfo != null ? new FileReference(resolvedPathInfo, this.project) : null;
    }

    @Nullable
    public FileReference resolveExternalLinkRef(@Nullable String linkRef, boolean withAnchor) {
        return resolveExternalLinkRef(linkRef, withAnchor, (LinkRefResolver) null);
    }

    @Nullable
    public FileReference resolveExternalLinkRef(@Nullable String linkRef) {
        return resolveExternalLinkRef(linkRef, false, (LinkRefResolver) null);
    }

    @NotNull
    @Override
    public FileReference withExt(@Nullable String ext) {
        return new FileReference(super.withExt(ext), project);
    }

    private boolean fileExists() {
        return getVirtualFile() != null;
    }

    public Project getProject() {
        return project;
    }

    @Nullable
    public VirtualFile getVirtualFile() {
        return FileReference.getVirtualFile(getFilePath());
    }

    @Nullable
    public VirtualFile getVirtualFileWithAnchor() {
        return FileReference.getVirtualFile(getFilePathWithAnchor());
    }

    @Nullable
    public VirtualFile getVirtualParent() {
        return FileReference.getVirtualFile(getPath());
    }

    @Nullable
    public PsiFile getPsiFile() {
        return FileReference.getPsiFile(getFilePath(), project);
    }

    @Nullable
    public PsiFile getPsiFileWithAnchor() {
        return FileReference.getPsiFile(getFilePathWithAnchor(), project);
    }

    @Nullable
    public MultiMarkdownFile getMultiMarkdownFile() {
        PsiFile file;
        return (file = FileReference.getPsiFile(getFilePath(), project)) instanceof MultiMarkdownFile ?
                (MultiMarkdownFile) file : null;
    }

    @Nullable
    public MultiMarkdownFile getMultiMarkdownFileWithAnchor() {
        PsiFile file;
        return (file = FileReference.getPsiFile(getFilePathWithAnchor(), project)) instanceof MultiMarkdownFile ?
                (MultiMarkdownFile) file : null;
    }

    @Nullable
    public static VirtualFile getVirtualFile(@NotNull String sourcePath) {
        return projectFileResolver == null ? null : projectFileResolver.getVirtualFile(sourcePath);
    }

    @Nullable
    public static PsiFile getPsiFile(@NotNull String sourcePath, @NotNull Project project) {
        return projectFileResolver == null ? null : projectFileResolver.getPsiFile(sourcePath, project);
    }

    @Override
    public int compareTo(FilePathInfo o) {
        return !(o instanceof FileReference) || project == ((FileReference) o).project ? super.compareTo(o) : -1;
    }

    @Override
    public String toString() {
        return "FileReference(" +
                innerString() +
                ")";
    }

    @Override
    public String innerString() {
        return super.innerString() +
                "project = '" + (project == null ? "null" : project.getName()) + "', " +
                "";
    }

    public boolean canRenameFileTo(@NotNull final String newName) {
        if (project != null) {
            if (equivalent(false, false, getFileName(), newName)) return true;

            // not just changing file name case
            final VirtualFile virtualFile = getVirtualFile();
            final VirtualFile parent = virtualFile != null ? virtualFile.getParent() : null;
            if (parent != null) {
                if (parent.findChild(newName) == null) {
                    return true;
                    //final boolean[] result = new boolean[1];
                    //
                    //ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    //    @Override
                    //    public void run() {
                    //        try {
                    //            VirtualFile newVirtualFile = parent.createChildData(this, newName);
                    //            result[0] = true;
                    //            try {
                    //                newVirtualFile.delete(this);
                    //            } catch (IOException ignore) {
                    //                logger.info("IOException on delete " + newName, ignore);
                    //            }
                    //        } catch (IOException ignore) {
                    //            // can't create it, so we remove it
                    //            logger.info("IOException on create " + newName, ignore);
                    //        }
                    //    }
                    //});
                    //
                    //return result[0];
                }
            }
        }
        return false;
    }

    public boolean canCreateFile() {
        final String newName = getFileName();
        return canCreateFile(newName);
    }

    public boolean canCreateFile(@NotNull final String newName) {
        if (project != null) {
            // not just changing file name case
            final VirtualFile parent = getVirtualParent();
            if (parent != null) {
                if (parent.findChild(newName) == null) {
                    return true;
                    //final boolean[] result = new boolean[1];
                    //
                    //Application application = ApplicationManager.getApplication();
                    //
                    //if (!application.isWriteAccessAllowed()) return true;
                    //
                    //application.runWriteAction(new Runnable() {
                    //    @Override
                    //    public void run() {
                    //        try {
                    //            VirtualFile newVirtualFile = parent.createChildData(this, newName);
                    //            result[0] = true;
                    //            try {
                    //                newVirtualFile.delete(this);
                    //            } catch (IOException ignore) {
                    //                logger.info("IOException on delete " + newName, ignore);
                    //            }
                    //        } catch (IOException ignore) {
                    //            // can't create it, so we remove it
                    //            logger.info("IOException on create " + newName, ignore);
                    //        }
                    //    }
                    //});
                    //return result[0];
                }
            }
        }
        return false;
    }
}