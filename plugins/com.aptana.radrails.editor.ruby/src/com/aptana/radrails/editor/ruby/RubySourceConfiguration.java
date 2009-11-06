/**
 * This file Copyright (c) 2005-2009 Aptana, Inc. This program is
 * dual-licensed under both the Aptana Public License and the GNU General
 * Public license. You may elect to use one or the other of these licenses.
 * 
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT. Redistribution, except as permitted by whichever of
 * the GPL or APL you select, is prohibited.
 *
 * 1. For the GPL license (GPL), you can redistribute and/or modify this
 * program under the terms of the GNU General Public License,
 * Version 3, as published by the Free Software Foundation.  You should
 * have received a copy of the GNU General Public License, Version 3 along
 * with this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Aptana provides a special exception to allow redistribution of this file
 * with certain other free and open source software ("FOSS") code and certain additional terms
 * pursuant to Section 7 of the GPL. You may view the exception and these
 * terms on the web at http://www.aptana.com/legal/gpl/.
 * 
 * 2. For the Aptana Public License (APL), this program and the
 * accompanying materials are made available under the terms of the APL
 * v1.0 which accompanies this distribution, and is available at
 * http://www.aptana.com/legal/apl/.
 * 
 * You may view the GPL, Aptana's exception and additional terms, and the
 * APL in the file titled license.html at the root of the corresponding
 * plugin containing this source file.
 * 
 * Any modifications to this file must keep this entire header intact.
 */

package com.aptana.radrails.editor.ruby;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.ISourceViewer;

import com.aptana.radrails.editor.common.CommonEditorPlugin;
import com.aptana.radrails.editor.common.IPartitioningConfiguration;
import com.aptana.radrails.editor.common.ISourceViewerConfiguration;
import com.aptana.radrails.editor.common.ISubPartitionScanner;
import com.aptana.radrails.editor.common.SubPartitionScanner;
import com.aptana.radrails.editor.common.theme.ThemeUtil;

/**
 * @author Max Stepanov
 * 
 */
public class RubySourceConfiguration implements IPartitioningConfiguration,
        ISourceViewerConfiguration {

    public final static String DEFAULT = "__rb" + IDocument.DEFAULT_CONTENT_TYPE;
    public static final String SINGLE_LINE_COMMENT = "__rb_singleline_comment"; //$NON-NLS-1$
    public static final String MULTI_LINE_COMMENT = "__rb_multiline_comment"; //$NON-NLS-1$
    public static final String REGULAR_EXPRESSION = "__rb_regular_expression"; //$NON-NLS-1$
    public static final String COMMAND = "__rb_command"; //$NON-NLS-1$
    public final static String STRING = "__rb_string"; //$NON-NLS-1$

    public static final String[] CONTENT_TYPES = new String[] {
    		DEFAULT,
    		SINGLE_LINE_COMMENT,
    		MULTI_LINE_COMMENT,
    		REGULAR_EXPRESSION,
    		COMMAND,
    		STRING
    	};

    private IToken stringToken = new Token(STRING);

    private IPredicateRule[] partitioningRules = new IPredicateRule[] {
            new EndOfLineRule("#", new Token(SINGLE_LINE_COMMENT)),
            new MultiLineRule("=begin", "=end", new Token(MULTI_LINE_COMMENT), (char) 0, true),
            new SingleLineRule("/", "/", new Token(REGULAR_EXPRESSION), '\\'),
            new SingleLineRule("\"", "\"", stringToken, '\\'),
            new SingleLineRule("\'", "\'", stringToken, '\\') };

    private RubyCodeScanner codeScanner;
    private RuleBasedScanner singleLineCommentScanner;
    private RuleBasedScanner multiLineCommentScanner;
    private RubyRegexpScanner regexpScanner;
    private RuleBasedScanner commandScanner;
    private RuleBasedScanner stringScanner;

    private static RubySourceConfiguration instance;

    public static RubySourceConfiguration getDefault() {
        if (instance == null) {
            instance = new RubySourceConfiguration();
        }
        return instance;
    }

    /**
     * @see com.aptana.radrails.editor.common.IPartitioningConfiguration#getContentTypes()
     */
    public String[] getContentTypes() {
        return CONTENT_TYPES;
    }

    /**
     * @see com.aptana.radrails.editor.common.IPartitioningConfiguration#getPartitioningRules()
     */
    public IPredicateRule[] getPartitioningRules() {
        return partitioningRules;
    }

    /**
     * @see com.aptana.radrails.editor.common.IPartitioningConfiguration#createSubPartitionScanner()
     */
    public ISubPartitionScanner createSubPartitionScanner() {
        return new SubPartitionScanner(partitioningRules, CONTENT_TYPES, new Token(DEFAULT));
    }

    /**
     * @see com.aptana.radrails.editor.common.ISourceViewerConfiguration#setupPresentationReconciler(org.eclipse.jface.text.presentation.PresentationReconciler,
     *      org.eclipse.jface.text.source.ISourceViewer)
     */
    public void setupPresentationReconciler(PresentationReconciler reconciler,
            ISourceViewer sourceViewer) {
        DefaultDamagerRepairer dr = new DefaultDamagerRepairer(getCodeScanner());
        reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
        reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

        dr = new DefaultDamagerRepairer(getSingleLineCommentScanner());
        reconciler.setDamager(dr, RubySourceConfiguration.SINGLE_LINE_COMMENT);
        reconciler.setRepairer(dr, RubySourceConfiguration.SINGLE_LINE_COMMENT);

        dr = new DefaultDamagerRepairer(getMultiLineCommentScanner());
        reconciler.setDamager(dr, RubySourceConfiguration.MULTI_LINE_COMMENT);
        reconciler.setRepairer(dr, RubySourceConfiguration.MULTI_LINE_COMMENT);

        dr = new DefaultDamagerRepairer(getRegexpScanner());
        reconciler.setDamager(dr, RubySourceConfiguration.REGULAR_EXPRESSION);
        reconciler.setRepairer(dr, RubySourceConfiguration.REGULAR_EXPRESSION);

        dr = new DefaultDamagerRepairer(getCommandScanner());
        reconciler.setDamager(dr, RubySourceConfiguration.COMMAND);
        reconciler.setRepairer(dr, RubySourceConfiguration.COMMAND);

        dr = new DefaultDamagerRepairer(getStringScanner());
        reconciler.setDamager(dr, RubySourceConfiguration.STRING);
        reconciler.setRepairer(dr, RubySourceConfiguration.STRING);
    }

    private ITokenScanner getCodeScanner() {
        if (codeScanner == null) {
            codeScanner = new RubyCodeScanner();
        }
        return codeScanner;
    }

    private ITokenScanner getMultiLineCommentScanner() {
        if (multiLineCommentScanner == null) {
            multiLineCommentScanner = new RuleBasedScanner();
            multiLineCommentScanner.setDefaultReturnToken(ThemeUtil.getToken("comment.block.rb")); //$NON-NLS-1$
        }
        return multiLineCommentScanner;
    }

    private ITokenScanner getSingleLineCommentScanner() {
        if (singleLineCommentScanner == null) {
            singleLineCommentScanner = new RuleBasedScanner();
            singleLineCommentScanner.setDefaultReturnToken(ThemeUtil
                    .getToken("comment.line.double-slash.rb")); //$NON-NLS-1$
        }
        return singleLineCommentScanner;
    }

    private ITokenScanner getRegexpScanner() {
        if (regexpScanner == null) {
            regexpScanner = new RubyRegexpScanner();
        }
        return regexpScanner;
    }

    private ITokenScanner getCommandScanner() {
        if (commandScanner == null) {
            commandScanner = new RuleBasedScanner();
            commandScanner.setDefaultReturnToken(new Token(new TextAttribute(CommonEditorPlugin
                    .getDefault().getColorManager().getColor(IRubyColorConstants.WORD))));
        }
        return commandScanner;
    }

    private ITokenScanner getStringScanner() {
        if (stringScanner == null) {
            stringScanner = new RuleBasedScanner();
            stringScanner.setDefaultReturnToken(new Token(new TextAttribute(CommonEditorPlugin
                    .getDefault().getColorManager().getColor(IRubyColorConstants.STRING))));
        }
        return stringScanner;
    }
}
