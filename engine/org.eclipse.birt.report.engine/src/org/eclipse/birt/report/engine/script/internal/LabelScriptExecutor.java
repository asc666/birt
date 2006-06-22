/*******************************************************************************
 * Copyright (c) 2005 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.script.internal;

import org.eclipse.birt.report.engine.api.script.element.ILabel;
import org.eclipse.birt.report.engine.api.script.eventhandler.ILabelEventHandler;
import org.eclipse.birt.report.engine.api.script.eventhandler.ITextItemEventHandler;
import org.eclipse.birt.report.engine.api.script.instance.ILabelInstance;
import org.eclipse.birt.report.engine.content.ILabelContent;
import org.eclipse.birt.report.engine.executor.ExecutionContext;
import org.eclipse.birt.report.engine.ir.LabelItemDesign;
import org.eclipse.birt.report.engine.ir.ReportItemDesign;
import org.eclipse.birt.report.engine.ir.TextItemDesign;
import org.eclipse.birt.report.engine.script.internal.element.Label;
import org.eclipse.birt.report.engine.script.internal.instance.LabelInstance;
import org.eclipse.birt.report.model.api.LabelHandle;
import org.eclipse.birt.report.model.api.TextItemHandle;

public class LabelScriptExecutor extends ScriptExecutor
{
	public static void handleOnPrepare( LabelHandle labelHandle,
			ExecutionContext context )
	{
		try
		{
			ILabel label = new Label( labelHandle );
			if ( handleJS( label, labelHandle.getOnPrepare( ), context )
					.didRun( ) )
				return;
			ILabelEventHandler eh = getEventHandler( labelHandle, context );
			if ( eh != null )
				eh.onPrepare( label, context.getReportContext( ) );
		} catch ( Exception e )
		{
			addException( context, e );
		}
	}

	public static void handleOnCreate( ILabelContent content,
			ExecutionContext context )
	{
		try
		{
			ReportItemDesign labelDesign = ( ReportItemDesign ) content
					.getGenerateBy( );
			ILabelInstance label = new LabelInstance( content, context );
			if ( handleJS( label, labelDesign.getOnCreate( ), context )
					.didRun( ) )
				return;
			ILabelEventHandler eh = getEventHandler( labelDesign, context );
			if ( eh != null )
				eh.onCreate( label, context.getReportContext( ) );
		} catch ( Exception e )
		{
			addException( context, e );
		}
	}

	public static void handleOnRender( ILabelContent content,
			ExecutionContext context )
	{
		try
		{
			ReportItemDesign labelDesign = ( ReportItemDesign ) content
					.getGenerateBy( );
			ILabelInstance label = new LabelInstance( content, context );
			if ( handleJS( label, labelDesign.getOnRender( ), context )
					.didRun( ) )
				return;
			ILabelEventHandler eh = getEventHandler( labelDesign, context );
			if ( eh != null )
				eh.onRender( label, context.getReportContext( ) );
		} catch ( Exception e )
		{
			addException( context, e );
		}
	}

	public static void handleOnPageBreak( ILabelContent content,
			ExecutionContext context )
	{
		try
		{
			ReportItemDesign labelDesign = ( ReportItemDesign ) content
					.getGenerateBy( );
			ILabelInstance label = new LabelInstance( content, context );
			if ( handleJS( label, labelDesign.getOnPageBreak( ), context )
					.didRun( ) )
				return;
			if ( labelDesign instanceof LabelItemDesign )
			{
				ILabelEventHandler eh = getEventHandler( labelDesign, context );
				if ( eh != null )
					eh.onPageBreak( label, context.getReportContext( ) );
			}
			else if ( labelDesign instanceof TextItemDesign )
			{
				ITextItemEventHandler eh = getEventHandler( labelDesign, context, true );
				if ( eh != null )
				{
					eh.onPageBreak( label, context.getReportContext( ) );
				}
			}
			
		} catch ( Exception e )
		{
			addException( context, e );
		}
	}

	private static ILabelEventHandler getEventHandler( ReportItemDesign design,
			ExecutionContext context )
	{
		LabelHandle handle = ( LabelHandle ) design.getHandle( );
		if ( handle == null )
			return null;
		return getEventHandler( handle, context );
	}

	private static ILabelEventHandler getEventHandler( LabelHandle handle,
			ExecutionContext context )
	{
		ILabelEventHandler eh = null;
		try
		{
			eh = ( ILabelEventHandler ) getInstance( handle, context );
		} catch ( ClassCastException e )
		{
			addClassCastException( context, e, handle.getEventHandlerClass( ),
					ILabelEventHandler.class );
		}
		return eh;
	}
	
	private static ITextItemEventHandler getEventHandler(
			ReportItemDesign design, ExecutionContext context, boolean isTextItem )
	{
		if (design.getHandle() instanceof TextItemHandle)
		{
			TextItemHandle handle = ( TextItemHandle ) design.getHandle( );
			if ( handle == null )
				return null;
			return getEventHandler( handle, context );
		}
		return null;
	}
	
	private static ITextItemEventHandler getEventHandler(
			TextItemHandle handle, ExecutionContext context )
	{
		ITextItemEventHandler eh = null;
		try
		{
			eh = ( ITextItemEventHandler ) getInstance( handle, context );
		} catch ( ClassCastException e )
		{
			addClassCastException( context, e, handle.getEventHandlerClass( ),
					ITextItemEventHandler.class );
		}
		return eh;
	}
}
