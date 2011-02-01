/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.findbar.impl;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.bindings.BindingManagerEvent;
import org.eclipse.jface.bindings.IBindingManagerListener;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.texteditor.ITextEditor;

import com.aptana.editor.findbar.FindBarPlugin;

/**
 * Helper to manage the activation of actions. When some control of the find bar receives the focus, the binding service
 * is disabled so that we no longer have the default actions from the text editor, while handling internally the actions
 * we want so that the actions related to the find bar still get executed. When focus is lost, the find bar actions get
 * disabled and the binding service is restored again.
 * 
 * @author Fabio Zadrozny
 */
public class FindBarActions
{

	private boolean fActivated;
	private IContextActivation findBarContextActivation;

	private final Map<String, AbstractHandler> fCommandToHandler = new HashMap<String, AbstractHandler>();
	private final List<IHandlerActivation> fHandlerActivations = new ArrayList<IHandlerActivation>();

	private final IBindingManagerListener fClearCommandToBindingOnChangesListener = new IBindingManagerListener()
	{

		public void bindingManagerChanged(BindingManagerEvent event)
		{
			if (event.isActiveBindingsChanged())
			{
				fCommandToBinding = null;
			}
		}
	};

	/**
	 * May be null.
	 */
	private HashMap<String, List<TriggerSequence>> fCommandToBinding;
	private ITextEditor textEditor;
	private WeakReference<FindBarDecorator> findBarDecorator;

	public FindBarActions(ITextEditor textEditor, FindBarDecorator findBarDecorator)
	{
		this.textEditor = textEditor;
		// Don't create cycles...
		this.findBarDecorator = new WeakReference<FindBarDecorator>(findBarDecorator);

		fCommandToHandler.put("org.eclipse.ui.edit.findbar.hide", new HideFindBarHandler()); //$NON-NLS-1$
		fCommandToHandler.put("org.eclipse.ui.edit.findbar.findPrevious", new FindPreviousHandler()); //$NON-NLS-1$
		fCommandToHandler.put("org.eclipse.ui.edit.findbar.findNext", new FindNextHandler()); //$NON-NLS-1$
		fCommandToHandler.put("org.eclipse.ui.edit.findbar.focusFind", new FocusFindFindBarHandler()); //$NON-NLS-1$
		fCommandToHandler.put("org.eclipse.ui.edit.findbar.focusReplace", new FocusReplaceFindBarHandler()); //$NON-NLS-1$

		// Now, aside from the find bar commands, there are some other commands that it's nice to have available too,
		// even if the editor does not have focus.
		fCommandToHandler.put("org.eclipse.ui.edit.undo", null); //$NON-NLS-1$
		fCommandToHandler.put("org.eclipse.ui.edit.redo", null); //$NON-NLS-1$
	}

	/**
	 * Focus listener that adds itself as a key adapter to a given control so that given some key sequence we activate
	 * the commands related to the find bar.
	 */
	private class FindBarControlFocusListener implements FocusListener, Listener
	{

		private Control control;
		private boolean listening = false;
		private Display display;

		public FindBarControlFocusListener(Control control)
		{
			this.control = control;
		}

		public void focusLost(FocusEvent e)
		{
			if (!listening)
			{
				return;
			}
			listening = false;
			setFindBarContextActive(false);
			display.removeFilter(SWT.KeyDown, this);
			display.removeFilter(SWT.Traverse, this);
		}

		public void focusGained(FocusEvent e)
		{
			if (listening)
			{
				return;
			}
			listening = true;
			display = PlatformUI.getWorkbench().getDisplay();
			display.addFilter(SWT.KeyDown, this);
			display.addFilter(SWT.Traverse, this);

			setFindBarContextActive(true);
		}

		/**
		 * Modeled as com.aptana.scripting.keybindings.internal.KeybindingsManager, which handles not only the KeyDown,
		 * but also the Traverse event so that it can consume events that would end up being treated in the mnemonics
		 * before actually issuing a KeyDown event.
		 */
		public void handleEvent(Event event)
		{

			// If this is not a keyboard event, then there are no key strokes. This can happen if we are listening to
			// focus traversal events.
			if ((event.stateMask == 0) && (event.keyCode == 0) && (event.character == 0))
			{
				return;
			}

			// We only want to handle events in the control that got the focus (the traverse will happen first at it,
			// so, if it can't consume this event, there's no point in checking bindings after that).
			if (event.widget != control)
			{
				return;
			}
			boolean consumed = processEvent(event.keyCode, event.stateMask);
			if (consumed)
			{
				consumeEvent(event);
			}
		}

		private void consumeEvent(Event event)
		{
			switch (event.type)
			{
				case SWT.KeyDown:
					event.doit = false;
					break;
				case SWT.Traverse:
					event.detail = SWT.TRAVERSE_NONE;
					event.doit = true;
					break;
				default:
			}
			event.type = SWT.NONE;
		}

		/**
		 * Matches key events against the keybindings for the find bar actions.
		 */
		private boolean processEvent(int keyCode, int stateMask)
		{
			boolean consumed = false;
			if (fCommandToBinding == null)
			{
				// May be changed to null if the list of bindings change.
				updateCommandToBinding();
			}

			HashMap<String, List<TriggerSequence>> commandToBinding = FindBarActions.this.fCommandToBinding;
			if (commandToBinding != null)
			{
				for (Map.Entry<String, List<TriggerSequence>> entry : commandToBinding.entrySet())
				{

					List<TriggerSequence> value = entry.getValue();
					for (TriggerSequence seq : value)
					{
						if (seq instanceof KeySequence)
						{
							KeySequence keySequence = (KeySequence) seq;
							if (KeyBindingHelper.matchesKeybinding(keyCode, stateMask, keySequence))
							{
								consumed = true;
								IHandlerService handlerService = (IHandlerService) textEditor.getSite().getService(
										IHandlerService.class);
								try
								{
									handlerService.executeCommand(entry.getKey(), null);
								}
								catch (NotEnabledException e1)
								{
									// Ignore it in this case (i.e.: undo will only be enabled if there's something
									// to be undone).
								}
								catch (Exception e1)
								{
									Status s = new Status(IStatus.ERROR, FindBarPlugin.PLUGIN_ID, IStatus.ERROR,
											e1.getMessage(), e1);
									FindBarPlugin.getDefault().getLog().log(s);
								}

								return consumed;
							}
						}
					}
				}
			}
			return consumed;
		}

	}

	private class HideFindBarHandler extends AbstractHandler
	{
		public Object execute(ExecutionEvent event) throws ExecutionException
		{
			FindBarDecorator dec = findBarDecorator.get();
			if (dec != null)
			{
				dec.hideFindBar();
			}
			return null;
		}
	}

	private class FindPreviousHandler extends AbstractHandler
	{
		public Object execute(ExecutionEvent event) throws ExecutionException
		{
			FindBarDecorator dec = findBarDecorator.get();
			if (dec != null)
			{
				dec.findPrevious();
			}
			return null;
		}
	}

	private class FindNextHandler extends AbstractHandler
	{
		public Object execute(ExecutionEvent event) throws ExecutionException
		{
			FindBarDecorator dec = findBarDecorator.get();
			if (dec != null)
			{
				dec.findNext();
			}
			return null;
		}
	}

	private class FocusFindFindBarHandler extends AbstractHandler
	{
		public Object execute(ExecutionEvent event) throws ExecutionException
		{
			FindBarDecorator dec = findBarDecorator.get();
			if (dec != null)
			{
				dec.combo.setFocus();
				dec.combo.setSelection(new Point(0, dec.combo.getText().length()));
			}
			return null;
		}
	}

	private class FocusReplaceFindBarHandler extends AbstractHandler
	{
		public Object execute(ExecutionEvent event) throws ExecutionException
		{
			FindBarDecorator dec = findBarDecorator.get();
			if (dec != null)
			{
				dec.comboReplace.setFocus();
			}
			return null;
		}
	}

	private void setFindBarContextActive(boolean activate)
	{
		fActivated = activate;
		IWorkbenchPartSite site = textEditor.getSite();
		IContextService contextService = (IContextService) site.getService(IContextService.class);
		IHandlerService handlerService = (IHandlerService) site.getService(IHandlerService.class);
		IBindingService service = (IBindingService) site.getService(IBindingService.class);

		if (activate)
		{

			// These will be the only active commands (note that they may have multiple keybindings
			// defined in plugin.xml)
			for (Map.Entry<String, AbstractHandler> entry : fCommandToHandler.entrySet())
			{
				AbstractHandler handler = entry.getValue();
				if (handler != null)
				{
					fHandlerActivations.add(handlerService.activateHandler(entry.getKey(), handler));
				}
			}

			// Yes, no longer execute anything from the binding service (we'll do our own handling so that the commands
			// we need still get executed).
			service.setKeyFilterEnabled(false);

			service.addBindingManagerListener(fClearCommandToBindingOnChangesListener);
			findBarContextActivation = contextService.activateContext("org.eclipse.ui.textEditorScope.findbar"); //$NON-NLS-1$
		}
		else
		{
			fCommandToBinding = null;
			service.setKeyFilterEnabled(true);
			if (findBarContextActivation != null)
			{
				service.removeBindingManagerListener(fClearCommandToBindingOnChangesListener);
				handlerService.deactivateHandlers(fHandlerActivations);
				fHandlerActivations.clear();
				contextService.deactivateContext(findBarContextActivation);
				findBarContextActivation = null;
			}
		}
	}

	/**
	 * Updates the list of commands -> binding available.
	 */
	public void updateCommandToBinding()
	{
		HashMap<String, List<TriggerSequence>> commandToBinding = new HashMap<String, List<TriggerSequence>>();

		IWorkbenchPartSite site = textEditor.getSite();
		IBindingService service = (IBindingService) site.getService(IBindingService.class);
		Binding[] bindings = service.getBindings();

		for (Map.Entry<String, AbstractHandler> entry : fCommandToHandler.entrySet())
		{
			List<TriggerSequence> seq = new ArrayList<TriggerSequence>();
			commandToBinding.put(entry.getKey(), seq);
			for (Binding binding : bindings)
			{
				ParameterizedCommand command = binding.getParameterizedCommand();
				if (command != null)
				{
					if (entry.getKey().equals(command.getId()))
					{
						seq.add(binding.getTriggerSequence());
					}
				}
			}

		}
		fCommandToBinding = commandToBinding;
	}

	public boolean isActivated()
	{
		return fActivated;
	}

	/**
	 * Creates a listener to manage the focus on buttons.
	 */
	public FocusListener createFocusListener(Button button)
	{
		return new FindBarControlFocusListener(button);
	}

	/**
	 * Creates a listener to manage the focus on combos.
	 */
	public FocusListener createFocusListener(Combo combo)
	{
		final WeakReference<Combo> weakCombo = new WeakReference<Combo>(combo);
		return new FindBarControlFocusListener(combo)
		{
			public void focusGained(FocusEvent e)
			{
				Combo c = weakCombo.get();
				if (c != null)
				{
					c.setForeground(null);
				}
				super.focusGained(e);
			}
		};
	}
}
