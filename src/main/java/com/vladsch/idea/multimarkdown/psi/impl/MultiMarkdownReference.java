/*
 * Copyright (c) 2015-2015 Vladimir Schneider <vladimir.schneider@gmail.com>, all rights reserved.
 *
 * This code is private property of the copyright holder and cannot be used without
 * having obtained a license or prior written permission of the of the copyright holder.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package com.vladsch.idea.multimarkdown.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.refactoring.rename.BindablePsiReference;
import com.intellij.util.IncorrectOperationException;
import com.vladsch.idea.multimarkdown.MultiMarkdownPlugin;
import com.vladsch.idea.multimarkdown.MultiMarkdownProjectComponent;
import com.vladsch.idea.multimarkdown.psi.MultiMarkdownNamedElement;
import com.vladsch.idea.multimarkdown.util.ReferenceChangeListener;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MultiMarkdownReference extends PsiReferenceBase<MultiMarkdownNamedElement> implements PsiPolyVariantReference, BindablePsiReference {
    private static final Logger logger = Logger.getLogger(MultiMarkdownReference.class);
    public static final ResolveResult[] EMPTY_RESULTS = new ResolveResult[0];
    protected ResolveResult[] resolveResults;
    protected String resolveResultsName;
    protected final ReferenceChangeListener referenceChangeListener;

    @Override
    public String toString() {
        //PsiElement resolve = resolve();
        return "Reference for " + myElement.toString();
    }

    public MultiMarkdownReference(@NotNull MultiMarkdownNamedElement element, @NotNull TextRange textRange) {
        super(element, textRange);

        final MultiMarkdownReference thizz = this;
        referenceChangeListener = new ReferenceChangeListener() {
            @Override
            public void referencesChanged(@Nullable String name) {
                synchronized (thizz) {
                    if (resolveResultsName != null && (name == null || resolveResultsName.equals(name))) invalidateResolveResults();
                }
            }
        };
    }

    protected void invalidateResolveResults() {
        synchronized (this) {
            //logger.info("invalidateResolveResults on " + this.toString());
            resolveResults = null;
            resolveResultsName = null;
            //logger.info("Invalidated resolve results" + " for " + myElement);
        }
    }

    protected void removeReferenceChangeListener() {
        if (myElement.getParent() != null) {
            MultiMarkdownProjectComponent projectComponent = MultiMarkdownPlugin.getProjectComponent(myElement.getProject());
            if (projectComponent != null) {
                projectComponent.removeListener(myElement.getMissingElementNamespace(), referenceChangeListener);
            }
        }
    }

    protected void addReferenceChangeListener() {
        if (myElement.getParent() != null) {
            MultiMarkdownProjectComponent projectComponent = MultiMarkdownPlugin.getProjectComponent(myElement.getProject());
            if (projectComponent != null) {
                projectComponent.addListener(myElement.getMissingElementNamespace(), referenceChangeListener);
            }
        }
    }

    @NotNull
    protected MultiMarkdownNamedElement getMissingLinkElement(@NotNull String name) {
        if (myElement.getParent() != null) {
            MultiMarkdownProjectComponent projectComponent = MultiMarkdownPlugin.getProjectComponent(myElement.getProject());
            if (projectComponent != null) {
                projectComponent.removeListener(myElement.getMissingElementNamespace(), referenceChangeListener);
                MultiMarkdownNamedElement referencedElement = projectComponent.getMissingLinkElement(myElement, myElement.getMissingElementNamespace(), name);
                projectComponent.addListener(myElement.getMissingElementNamespace(), referenceChangeListener);
                return referencedElement;
            }
        }
        return myElement;
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        synchronized (this) {
            if (resolveResults == null || resolveResultsName == null || !resolveResultsName.equals(getElement().getName())) {
                resolveResultsName = getElement().getName();
                if (resolveResultsName == null) resolveResultsName = "";
                setRangeInElement(new TextRange(0, resolveResultsName.length()));
                resolveResults = getMultiResolveResults(incompleteCode);
            }
            return resolveResults;
        }
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        // we will handle this by renaming the element to point to the new location
        if (element.getClass() == myElement.getClass()) {
            String name = ((MultiMarkdownNamedElement) element).getName();
            // this will create a new reference and loose connection to this one
            // logger.info("rebinding " + myElement + " to " + element);
            if (name != null) return myElement.setName(name, MultiMarkdownNamedElement.REASON_FILE_MOVED);
        }
        throw new IncorrectOperationException("Rebind cannot be performed for " + getClass());
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        //List<LookupElement> variants = new ArrayList<LookupElement>();
        //Project project = myElement.getProject();
        //List<MultiMarkdownFile> wikiFiles = MultiMarkdownUtil.findWikiFiles(project,
        //        myElement.getContainingFile() instanceof MultiMarkdownFile && ((MultiMarkdownFile) myElement.getContainingFile()).isWikiPage());
        //for (final MultiMarkdownFile wikFile : wikiFiles) {
        //    if (wikFile.isPageReference(name, myElement.getContainingFile().getVirtualFile())) {
        //        variants.add(LookupElementBuilder.create(wikFile).
        //                        withIcon(MultiMarkdownIcons.FILE).
        //                        withTypeText(wikFile.getContainingFile().getName())
        //        );
        //    }
        //}
        //return variants.toArray();
        return new Object[0];
    }

    @NotNull
    protected abstract ResolveResult[] getMultiResolveResults(boolean incompleteCode);
}