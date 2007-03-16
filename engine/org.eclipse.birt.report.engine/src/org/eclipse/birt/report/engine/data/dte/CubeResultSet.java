/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.data.dte;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.olap.OLAPException;
import javax.olap.cursor.CubeCursor;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.data.engine.api.IBaseExpression;
import org.eclipse.birt.data.engine.api.IBaseQueryResults;
import org.eclipse.birt.data.engine.olap.api.ICubeQueryResults;
import org.eclipse.birt.data.engine.olap.api.query.ICubeQueryDefinition;
import org.eclipse.birt.report.engine.api.DataSetID;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.data.IDataEngine;
import org.eclipse.birt.report.engine.executor.ExecutionContext;
import org.eclipse.birt.report.engine.extension.IBaseResultSet;
import org.eclipse.birt.report.engine.extension.ICubeResultSet;

public class CubeResultSet implements ICubeResultSet
{

	protected IBaseResultSet parent;

	protected DataSetID id;

	protected String cellId;

	/**
	 * data engine
	 */
	protected IDataEngine dataEngine = null;

	private ExecutionContext context;

	private ICubeQueryDefinition queryDefn;

	private CubeCursor cube;

	private ICubeQueryResults queryResults;

	protected static Logger logger = Logger.getLogger( CubeResultSet.class
			.getName( ) );

	// Top level query results
	public CubeResultSet( IDataEngine dataEngine, ExecutionContext context,
			ICubeQueryDefinition queryDefn, ICubeQueryResults rsets )
			throws BirtException
	{
		this.parent = null;
		this.context = context;
		this.dataEngine = dataEngine;
		this.queryDefn = queryDefn;
		this.id = new DataSetID( rsets.getID( ) );
		this.cube = rsets.getCubeCursor( );
		this.queryResults = rsets;
	}

	// Nest query
	public CubeResultSet( IDataEngine dataEngine, ExecutionContext context,
			IBaseResultSet parent, ICubeQueryDefinition queryDefn,
			ICubeQueryResults rsets ) throws BirtException
	{
		assert parent != null;
		this.parent = parent;
		this.id = new DataSetID( rsets.getID( ) );
		this.context = context;
		this.dataEngine = dataEngine;
		this.queryDefn = queryDefn;
		this.cube = rsets.getCubeCursor( );
		this.queryResults = rsets;
	}

	public CubeCursor getCubeCursor( )
	{
		return cube;
	}

	public String getCellIndex( )
	{
		return cellId;
	}

	public void close( )
	{
		// remove the data set from the data set list
		dataEngine.close( this );
		try
		{
			if ( queryResults != null )
			{
				queryResults.close( );
			}
			if ( cube != null )
			{
				cube.close( );
			}
		}
		catch ( Exception ex )
		{
			logger.log( Level.SEVERE, ex.getMessage( ), ex );
			// context.addException( ex );
		}
	}

	public Object evaluate( String expr ) throws BirtException
	{
		return null;
	}

	public Object evaluate( IBaseExpression expr ) throws BirtException
	{
		return null;
	}

	public String getCubeIndex( )
	{
		return cellId;
	}

	public DataSetID getID( )
	{
		return id;
	}

	public IBaseResultSet getParent( )
	{
		return parent;
	}

	public IBaseQueryResults getQueryResults( )
	{
		return queryResults;
	}

	public String getRawID( )
	{
		try
		{
			return cube.getId( );
		}
		catch ( OLAPException e )
		{
			context.addException( new EngineException( "get RawID error" ) );
		}
		return null;
	}

	public int getType( )
	{
		return CUBE_RESULTSET;
	}

	public void skipTo( String cubeIndex )
	{

	}

}
