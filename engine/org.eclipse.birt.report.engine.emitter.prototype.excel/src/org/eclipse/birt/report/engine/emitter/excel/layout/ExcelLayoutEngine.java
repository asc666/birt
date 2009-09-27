/*******************************************************************************
 * Copyright (c) 2004, 2008Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.emitter.excel.layout;

import java.io.IOException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.birt.report.engine.content.ICellContent;
import org.eclipse.birt.report.engine.content.IContainerContent;
import org.eclipse.birt.report.engine.content.IDataContent;
import org.eclipse.birt.report.engine.content.IHyperlinkAction;
import org.eclipse.birt.report.engine.content.IImageContent;
import org.eclipse.birt.report.engine.content.IStyle;
import org.eclipse.birt.report.engine.emitter.EmitterUtil;
import org.eclipse.birt.report.engine.emitter.excel.BlankData;
import org.eclipse.birt.report.engine.emitter.excel.BookmarkDef;
import org.eclipse.birt.report.engine.emitter.excel.Data;
import org.eclipse.birt.report.engine.emitter.excel.DataCache;
import org.eclipse.birt.report.engine.emitter.excel.DateTimeUtil;
import org.eclipse.birt.report.engine.emitter.excel.ExcelEmitter;
import org.eclipse.birt.report.engine.emitter.excel.ExcelUtil;
import org.eclipse.birt.report.engine.emitter.excel.HyperlinkDef;
import org.eclipse.birt.report.engine.emitter.excel.ImageData;
import org.eclipse.birt.report.engine.emitter.excel.RowData;
import org.eclipse.birt.report.engine.emitter.excel.SheetData;
import org.eclipse.birt.report.engine.emitter.excel.Span;
import org.eclipse.birt.report.engine.emitter.excel.StyleBuilder;
import org.eclipse.birt.report.engine.emitter.excel.StyleConstant;
import org.eclipse.birt.report.engine.emitter.excel.StyleEngine;
import org.eclipse.birt.report.engine.emitter.excel.StyleEntry;
import org.eclipse.birt.report.engine.emitter.excel.BlankData.Type;
import org.eclipse.birt.report.engine.i18n.EngineResourceHandle;
import org.eclipse.birt.report.engine.i18n.MessageConstants;
import org.eclipse.birt.report.engine.layout.emitter.Image;
import org.eclipse.birt.report.engine.layout.pdf.util.PropertyUtil;
import org.eclipse.birt.report.engine.util.FlashFile;

import com.ibm.icu.util.ULocale;


public class ExcelLayoutEngine
{

	protected static Logger logger = Logger.getLogger( ExcelLayoutEngine.class
			.getName( ) );

	public final static String EMPTY = "";
	
	public static final double DEFAULT_ROW_HEIGHT = 15;

	// Excel 2007 can support 1048576 rows and 16384 columns.
	private int autoBookmarkIndex = 0;

	public static final String AUTO_GENERATED_BOOKMARK = "auto_generated_bookmark_";

	public final static int MAX_ROW_OFFICE2007 = 1048576;
	
	public final static int MAX_COL_OFFICE2007 = 16384;
	
	public final static int MAX_ROW_OFFICE2003 = 65535;
	
	public final static int MAX_COLUMN_OFFICE2003 = 255;
	
	private int maxRow = MAX_ROW_OFFICE2003;

	private int maxCol = MAX_COLUMN_OFFICE2003;
	
	private HashMap<String, String> cachedBookmarks = new HashMap<String, String>( );

	protected DataCache cache;

	private AxisProcessor axis;

	protected StyleEngine engine;
	
	private ExcelEmitter emitter;

	private Stack<XlsContainer> containers = new Stack<XlsContainer>( );

	private Stack<XlsTable> tables = new Stack<XlsTable>( );

	private ExcelContext context = null;

	private String messageFlashObjectNotSupported;
	
	private ULocale locale;

	private HashMap<String, BookmarkDef> bookmarkList = new HashMap<String, BookmarkDef>( );

	public ExcelLayoutEngine( ExcelContext context,
			ExcelEmitter emitter )
	{
		this.context = context;
		this.emitter = emitter;
		this.locale = context.getLocale( );
		EngineResourceHandle resourceHandle = new EngineResourceHandle( locale );
		messageFlashObjectNotSupported = resourceHandle
				.getMessage( MessageConstants.FLASH_OBJECT_NOT_SUPPORTED_PROMPT );
	}
	
	public void initalize( PageDef page )
	{
		axis = new AxisProcessor( );		
		axis.addCoordinate( page.contentwidth );

		setCacheSize();
		
		ContainerSizeInfo rule = new ContainerSizeInfo( 0, page.contentwidth );
		cache = createDataCache( maxCol, maxRow );
		engine = new StyleEngine( this );
		containers.push( createContainer( rule, page.style, null ) );
	}

	protected DataCache createDataCache( int maxColumn, int maxRow )
	{
		return new DataCache( maxColumn, maxRow );
	}
	
	private void setCacheSize()
	{
		if ( context.getOfficeVersion( ).equals( "office2007" ) )
		{
			maxCol = MAX_COL_OFFICE2007;
		}
	}

	public XlsContainer getCurrentContainer( )
	{
		return (XlsContainer) containers.peek( );
	}

	public Stack<XlsContainer> getContainers( )
	{
		return containers;
	}

	public void setPageStyle( IStyle style )
	{
		XlsContainer topContainer = containers.peek( );
		topContainer.setStyle( StyleBuilder.createStyleEntry( style ) );
	}
	
	public void addTable( IContainerContent content, ColumnsInfo table,
			ContainerSizeInfo size )
	{
		IStyle style = content.getComputedStyle( );
		XlsContainer currentContainer = getCurrentContainer( );
		ContainerSizeInfo parentSizeInfo = currentContainer.getSizeInfo( );
		int[] columnStartCoordinates = splitColumns( table, parentSizeInfo );
		createTable( table, style, currentContainer, columnStartCoordinates );
	}

	protected int[] splitColumns( ColumnsInfo columnsInfo,
			ContainerSizeInfo parentSizeInfo )
	{
		int startCoordinate = parentSizeInfo.getStartCoordinate( );
		int endCoordinate = parentSizeInfo.getEndCoordinate( );

		int[] columnStartCoordinates = calculateColumnCoordinates( columnsInfo,
				startCoordinate, endCoordinate );

		splitColumns( startCoordinate, endCoordinate, columnStartCoordinates );
		return columnStartCoordinates;
	}

	private void createTable( ColumnsInfo tableInfo, IStyle style,
			XlsContainer currentContainer, int[] columnStartCoordinates )
	{
		int leftCordinate = columnStartCoordinates[0];
		int width = columnStartCoordinates[columnStartCoordinates.length - 1]
				- leftCordinate;
		ContainerSizeInfo sizeInfo = new ContainerSizeInfo( leftCordinate,
				width );
		StyleEntry styleEntry = engine.createEntry( sizeInfo, style );
		XlsTable table = new XlsTable( tableInfo, styleEntry,
				sizeInfo, currentContainer );
		tables.push( table );
		addContainer( table );
	}

	private void splitColumns( int startCoordinate, int endCoordinate,
			int[] columnStartCoordinates )
	{
		int[] scale = axis.getColumnCoordinatesInRange( startCoordinate,
				endCoordinate );

		for ( int i = 0; i < scale.length - 1; i++ )
		{
			int startPosition = scale[i];
			int endPostion = scale[i + 1];

			int[] range = inRange( startPosition, endPostion, columnStartCoordinates );

			if ( range.length > 0 )
			{				
				int pos = axis.getColumnIndexByCoordinate( startPosition );
				cache.insertColumns( pos, range.length );

				for ( int j = 0; j < range.length; j++ )
				{
					axis.addCoordinate( range[j] );
				}
			}
		}
	}

	private int[] calculateColumnCoordinates( ColumnsInfo table,
			int startCoordinate,
			int endCoordinate )
	{
		int columnCount = table.getColumnCount( );
		int[] columnStartCoordinates = new int[ columnCount + 1 ];
		columnStartCoordinates[0] = startCoordinate;
		for ( int i = 1; i <= columnCount; i++ )
		{
			if ( ( columnStartCoordinates[i - 1] + table.getColumnWidth( i - 1 ) ) > endCoordinate )
			{
				columnStartCoordinates[i] = endCoordinate;
			}
			else
				columnStartCoordinates[i] = columnStartCoordinates[i - 1]
				+ table.getColumnWidth( i - 1 );
		}
		return columnStartCoordinates;
	}

	private int[] inRange( int start, int end, int[] data )
	{
		int[] range = new int[data.length];
		int count = 0;

		for ( int i = 0; i < data.length; i++ )
		{
			if ( ( data[i] > start ) && ( data[i] < end ) )
			{
				count++;
				range[count] = data[i];
			}
		}

		int[] result = new int[count];

		int j = 0;
		for ( int i = 0; i < range.length; i++ )
		{
			if ( range[i] != 0 )
			{
				result[j] = range[i];
				j++;
			}
		}

		return result;
	}

	public void addCell( int col, int colSpan, int rowSpan, IStyle style )
	{
		XlsTable table = tables.peek( );
		ContainerSizeInfo cellSizeInfo = table.getColumnSizeInfo( col, colSpan );
		XlsCell cell = new XlsCell( engine.createEntry( cellSizeInfo, style ),
				cellSizeInfo, getCurrentContainer( ), rowSpan );
		addContainer( cell );
	}

	public void addCell( ICellContent cellcontent, int col, int colSpan,
			int rowSpan, IStyle style )
	{
		XlsTable table = tables.peek( );
		ContainerSizeInfo cellSizeInfo = table.getColumnSizeInfo( col, colSpan );
		int diagonalNumber = cellcontent.getDiagonalNumber( );
		StyleEntry cellStyleEntry = null;
		if ( diagonalNumber != 0 )
		{
			String diagonalColor = cellcontent.getDiagonalColor( );
			String diagonalStyle = cellcontent.getDiagonalStyle( );
			int diagonalWidth = PropertyUtil.getDimensionValue( cellcontent,
					cellcontent.getDiagonalWidth( ), cellSizeInfo.getWidth( ) );
			cellStyleEntry = engine.createCellEntry( cellSizeInfo, style,
					diagonalColor, diagonalStyle, diagonalWidth );
		}
		else
		{
			cellStyleEntry = engine.createEntry( cellSizeInfo, style );
		}
		XlsCell cell = new XlsCell( cellStyleEntry, cellSizeInfo,
				getCurrentContainer( ), rowSpan );
		addContainer( cell );
	}

	public void endCell( )
	{
		endNormalContainer( );
	}

	public void addRow( IStyle style )
	{
		XlsContainer parent = getCurrentContainer( );
		ContainerSizeInfo sizeInfo = parent.getSizeInfo( );
		XlsContainer container = createContainer( sizeInfo, style,
				parent );
		container.setEmpty( false );
		addContainer( container );
	}

	public void endRow( double rowHeight )
	{
		synchronize( rowHeight );
		endContainer( );
	}

	private void synchronize( double height )
	{
		XlsContainer rowContainer = getCurrentContainer( );
		ContainerSizeInfo rowSizeInfo = rowContainer.getSizeInfo( );
		int startCoordinate = rowSizeInfo.getStartCoordinate( );
		int endCoordinate = rowSizeInfo.getEndCoordinate( );
		int startColumnIndex = axis.getColumnIndexByCoordinate( startCoordinate );
		int endColumnIndex = axis.getColumnIndexByCoordinate( endCoordinate );

		int maxRowIndex = 0;
		int rowIndexes[] = new int[endColumnIndex - startColumnIndex];

		for ( int currentColumnIndex = startColumnIndex; currentColumnIndex < endColumnIndex; currentColumnIndex++ )
		{
			int rowIndex = cache.getMaxRowIndex( currentColumnIndex );
			SheetData lastData = cache.getColumnLastData( currentColumnIndex );
			rowIndexes[currentColumnIndex - startColumnIndex] = rowIndex;
			int span = lastData != null ? lastData.getRowSpanInDesign( ) : 0;
			if ( span == 0
					|| ( span == 1 && !isInContainer( lastData, rowContainer ) ) )
			{
				maxRowIndex = maxRowIndex > rowIndex ? maxRowIndex : rowIndex;
			}
		}
		int startRowIndex = rowContainer.getRowIndex( );
		if ( maxRowIndex == startRowIndex )
		{
			maxRowIndex++;
		}
		rowContainer.setRowIndex( maxRowIndex );
		double resize = height / ( maxRowIndex - startRowIndex );
		for ( int i = startRowIndex; i < maxRowIndex; i++ )
		{
			cache.setRowHeight( i, resize );
		}

		for ( int currentColumnIndex = startColumnIndex; currentColumnIndex < endColumnIndex; currentColumnIndex++ )
		{
			int rowspan = maxRowIndex - rowIndexes[currentColumnIndex - startColumnIndex];
			SheetData upstair = cache.getColumnLastData( currentColumnIndex );
			if ( rowspan > 0 )
			{
				if ( upstair != null && canSpan( upstair, rowContainer ) )
				{
					Type blankType = Type.VERTICAL;
					if ( upstair.isBlank( ) )
					{
						BlankData blankData = (BlankData) upstair;
						if ( blankData.getType( ) == Type.VERTICAL )
						{
							upstair
									.setRowSpan( upstair.getRowSpan( )
											+ rowspan );
							if ( !isInContainer( blankData, rowContainer ) )
							{
								upstair.decreasRowSpanInDesign( );
							}
						}
						blankType = blankData.getType( );
					}
					else
					{
						upstair.setRowSpan( upstair.getRowSpan( ) + rowspan );
						if ( !isInContainer( upstair, rowContainer ) )
						{
							upstair.decreasRowSpanInDesign( );
						}
					}
					int rowIndex = upstair.getRowIndex( );
					for ( int p = 1; p <= rowspan; p++ )
					{
						BlankData blank = new BlankData( upstair );
						blank.setRowIndex( rowIndex + p );
						blank.setType( blankType );
						cache.addData( currentColumnIndex, blank );
					}
				}
			}
			else if ( upstair.getRowSpanInDesign( ) > 0
					&& !isInContainer( upstair, rowContainer ) )
			{
				upstair.decreasRowSpanInDesign( );
			}
		}
	}

	private void calculateRowHeight( SheetData[] rowData, boolean isAuto )
	{
		double rowHeight = 0;
		int rowIndex = getRowIndex( rowData );
		double lastRowHeight = rowIndex > 0
				? cache.getRowHeight( rowIndex - 1 )
				: 0;
		boolean hasCurrentRowHeight = cache.hasRowHeight( rowIndex );
		if ( !hasCurrentRowHeight || isAuto )
		{
			for ( int i = 0; i < rowData.length; i++ )
			{
				SheetData data = rowData[i];
				if ( data != null )
				{
					if ( data.isBlank( ) )
					{
						// if the data spans last row,then recalculate data
						// height.
						// if current row is the last row of real data, then
						// adjust
						// row height.
						BlankData blankData = (BlankData) data;
						if ( blankData.getType( ) == Type.VERTICAL )
						{
							data.setHeight( data.getHeight( ) - lastRowHeight );
						}
					}
					SheetData realData = getRealData( data );
					if ( realData != null )
					{
						int realDataRowEnd = realData.getRowIndex( )
								+ realData.getRowSpan( );
						if ( realDataRowEnd == data.getRowIndex( ) )
						{
							rowHeight = data.getHeight( ) > rowHeight ? data
									.getHeight( ) : rowHeight;
						}
					}
				}
			}
			cache.setRowHeight( rowIndex, rowHeight );
		}
	}

	private int getRowIndex( SheetData[] rowData )
	{
		for ( int j = 0; j < rowData.length; j++ )
		{
			SheetData data = rowData[j];
			if ( data != null )
			{
				return data.getRowIndex( ) - 1;
			}
		}
		return 0;
	}

	private boolean canSpan( SheetData data, XlsContainer rowContainer )
	{
		SheetData realData = getRealData(data);
		if ( realData == null )
			return false;
		if ( isInContainer( realData, rowContainer ) )
		{
			return true;
		}
		return realData.getRowSpanInDesign( ) > 0;
	}

	private SheetData getRealData( SheetData data )
	{
		while ( data != null && data.isBlank( ) )
		{
			data = ( (BlankData) data ).getData( );
		}
		return data;
	}

	private boolean isInContainer( SheetData data, XlsContainer rowContainer )
	{
		return data.getRowIndex( ) >= rowContainer.getStartRowId( );
	}

	public void endTable( )
	{
		if ( !tables.isEmpty( ) )
		{
			tables.pop( );
			endContainer( );
		}
	}

	public void addContainer( IStyle style, HyperlinkDef link )
	{
		XlsContainer parent = getCurrentContainer( );
		ContainerSizeInfo sizeInfo = parent.getSizeInfo( );
		StyleEntry entry = engine.createEntry( sizeInfo, style );
		addContainer( new XlsContainer( entry, sizeInfo, parent ) );
	}

	private void addContainer( XlsContainer child )
	{
		XlsContainer parent = child.getParent( );
		if ( parent instanceof XlsCell )
		{
			addEmptyDataToContainer( child, parent );
		}
		if ( parent != null )
		{
			parent.setEmpty( false );
		}
		containers.push( child);
	}

	private void addEmptyDataToContainer( XlsContainer child,
			XlsContainer parent )
	{
		ContainerSizeInfo childSizeInfo = child.getSizeInfo( );
		int childStartCoordinate = childSizeInfo.getStartCoordinate( );
		int childEndCoordinate = childSizeInfo.getEndCoordinate( );
		ContainerSizeInfo parentSizeInfo = parent.getSizeInfo( );
		int parentStartCoordinate = parentSizeInfo.getStartCoordinate( );
		int parentEndCoordinate = parent.getSizeInfo( ).getEndCoordinate( );

		if ( childEndCoordinate < parentEndCoordinate )
		{
			StyleEntry style = parent.getStyle( );
			removeLeftBorder( style );
			removeDiagonalLine( style );
			addEmptyDataToContainer( style, parent, childEndCoordinate,
					parentEndCoordinate - childEndCoordinate );
		}
		if ( childStartCoordinate > parentStartCoordinate )
		{
			StyleEntry style = parent.getStyle( );
			removeRightBorder( style );
			removeDiagonalLine( style );
			addEmptyDataToContainer( style, parent, childStartCoordinate,
					parentStartCoordinate - childStartCoordinate );
		}
	}

	private void addEmptyDataToContainer( StyleEntry style,
			XlsContainer parent, int startCoordinate, int width )
	{
		Data data = createEmptyData( style );
		data.setSizeInfo( new ContainerSizeInfo( startCoordinate, width ) );
		addData( data );
	}

	public void endContainer( )
	{
		setParentContainerIndex( );
		endNormalContainer( );
	}

	private void setParentContainerIndex( )
	{
		XlsContainer container = getCurrentContainer( );
		XlsContainer parent = container.getParent( );
		if ( parent != null )
			parent.setRowIndex( container.getRowIndex( ) );
	}

	public void endNormalContainer( )
	{
		XlsContainer container = getCurrentContainer( );
		if ( container.isEmpty( ) )
		{
			Data data = createData( EMPTY, container.getStyle( ), Data.STRING );
			data.setSizeInfo( container.getSizeInfo( ) );
			addData( data );
		}
		engine.applyContainerBottomStyle( );
		containers.pop( );
	}

	public Data addData( Object txt, IStyle style, HyperlinkDef link,
			BookmarkDef bookmark, double height )
	{
		return addData( txt, style, link, bookmark, null, height );
	}

	public Data addData( Object txt, IStyle style, HyperlinkDef link,
			BookmarkDef bookmark, String dataLocale, double height )
	{
		ContainerSizeInfo rule = getCurrentContainer( ).getSizeInfo( );
		StyleEntry entry = engine.getStyle( style, rule );
		Data data = createData( txt, entry, dataLocale );
		data.setHeight( height );
		data.setHyperlinkDef( link );
		data.setBookmark( bookmark );
		data.setSizeInfo( rule );
		addData( data );
		return data;
	}

	public void addImageData( IImageContent image, IStyle style,
			HyperlinkDef link, BookmarkDef bookmark )
	{
		XlsContainer container=getCurrentContainer();
		ContainerSizeInfo parentSizeInfo = container.getSizeInfo( );
		ColumnsInfo imageColumnsInfo = LayoutUtil.createImage( image, parentSizeInfo
				.getWidth( ) );
		splitColumns( imageColumnsInfo, parentSizeInfo );
		ContainerSizeInfo imageSizeInfo = new ContainerSizeInfo( parentSizeInfo
				.getStartCoordinate( ), imageColumnsInfo.getTotalWidth( ) );
		StyleEntry entry = engine.getStyle( style, imageSizeInfo,
				parentSizeInfo );
		SheetData data = createImageData( image, entry,container );
		data.setHyperlinkDef( link );
		data.setBookmark( bookmark );
		data.setSizeInfo( imageSizeInfo );
		addData( data );
	}

	private SheetData createImageData( IImageContent image, StyleEntry entry, XlsContainer container )
	{
		int type = SheetData.IMAGE;
		entry.setProperty( StyleConstant.DATA_TYPE_PROP, type );
		String uri = image.getURI( );
		String mimeType = image.getMIMEType( );
		String extension = image.getExtension( );
		String altText = image.getAltText( );
		if ( FlashFile.isFlash( mimeType, uri, extension ) )
		{
			if ( null == altText )
			{
				altText = messageFlashObjectNotSupported; 
			}
			return createData( altText, entry);
		}

		try
		{
			Image imageInfo = EmitterUtil.parseImage( image, image
					.getImageSource( ), image.getURI( ), image.getMIMEType( ),
					image.getExtension( ) );

			byte[] data = imageInfo.getData( );
			if ( data != null )
			{
				return createData( image, entry, container, type, imageInfo );
			}
			else
			{
				return createData( image.getAltText( ), entry );
			}
		}
		catch ( IOException e )
		{
			logger.log( Level.WARNING, e.getLocalizedMessage( ) );
			return createData( image.getAltText( ), entry );
		}
	}

	protected SheetData createData( IImageContent image, StyleEntry entry,
			XlsContainer container, int type, Image imageInfo )
	{
		SheetData imageData = new ImageData( image, entry, type, imageInfo,
				container );
		return imageData;
	}

	public Data addDateTime( Object txt, IStyle style, HyperlinkDef link,
			BookmarkDef bookmark, String dateTimeLocale, double height )
	{
		ContainerSizeInfo rule = getCurrentContainer( ).getSizeInfo( );
		StyleEntry entry = engine.getStyle( style, rule );
		Data data = null;
		
		IDataContent dataContent = (IDataContent)txt;
		Object value = dataContent.getValue( );
		Date date = ExcelUtil.getDate( value );
		//If date time is before 1900, it must be output as string, otherwise, excel can't format the date.
		if ( date != null
				&& ( ( date instanceof Time ) || date.getYear( ) >= 0 ) )
		{
			data = createDateData( value, entry, style.getDateTimeFormat( ),
					dateTimeLocale );
			data.setHeight( height );
			data.setHyperlinkDef( link );
			data.setBookmark( bookmark );
			data.setSizeInfo( rule );
			addData( data );
			return data;
		}
		else
		{
			return addData( dataContent.getText( ), style, link, bookmark,
					dateTimeLocale, height );
		}
	}

	public void addCaption( String text, IStyle style )
	{
		ContainerSizeInfo rule = getCurrentContainer( ).getSizeInfo( );
		StyleEntry entry = StyleBuilder.createEmptyStyleEntry( );
		entry.setProperty( StyleEntry.H_ALIGN_PROP, "Center" );
		entry.setProperty( StyleEntry.FONT_SIZE_PROP, StyleBuilder
				.convertFontSize( style.getFontSize( ) ) );
		Data data = createData( text, entry );
		data.setSizeInfo( rule );

		addData( data );
	}

	private Data createData( Object txt, StyleEntry entry )
	{
		return createData( txt, entry, getLocale( null ) );
	}

	protected Data createData( Object txt, StyleEntry entry, String dlocale )
	{
		return createData( txt, entry, getLocale( dlocale ) );
	}

	private Data createData( Object txt, StyleEntry entry, ULocale dataLocale )
	{
		int type = SheetData.STRING;
		if ( SheetData.NUMBER==ExcelUtil.getType( txt )  )
		{
			String format = ExcelUtil.getPattern( txt, (String) entry
					.getProperty( StyleConstant.NUMBER_FORMAT_PROP ) );
			format = ExcelUtil.formatNumberPattern( format, dataLocale );
			entry.setProperty( StyleConstant.NUMBER_FORMAT_PROP, format );
			type = SheetData.NUMBER;

		}
		else if ( SheetData.DATE == ExcelUtil.getType( txt ) )
		{
			String format = ExcelUtil.getPattern( txt, (String) entry
					.getProperty( StyleConstant.DATE_FORMAT_PROP ) );
			entry.setProperty( StyleConstant.DATE_FORMAT_PROP, format );
			type = Data.DATE;
		}

		entry.setProperty( StyleConstant.DATA_TYPE_PROP, type );

		return createData( txt, entry, type );
	}

	private Data createDateData( Object txt, StyleEntry entry,
			String timeFormat, String dlocale )
	{
		ULocale dateLocale = getLocale( dlocale );
		timeFormat = ExcelUtil.parse( txt, timeFormat, dateLocale );
		timeFormat = DateTimeUtil.formatDateTime( timeFormat, dateLocale );
		entry.setProperty( StyleConstant.DATE_FORMAT_PROP, timeFormat );
		entry.setProperty( StyleConstant.DATA_TYPE_PROP, SheetData.DATE );
		return createData( txt, entry, SheetData.DATE );
	}

	private ULocale getLocale( String dlocale )
	{
		return dlocale == null ? locale : new ULocale( dlocale );
	}

	protected void addData( SheetData data )
	{
		XlsContainer container = getCurrentContainer( );
		container.setEmpty( false );
		int col = axis.getColumnIndexByCoordinate( data.getSizeInfo( ).getStartCoordinate( ) );
		if ( col == -1 || col >= cache.getColumnCount( ) )
			return;
		int span = axis.getColumnIndexByCoordinate( data.getSizeInfo( ).getEndCoordinate( ) ) - col;
		applyTopBorderStyle( data );
		// FIXME: there is a bug when this data is in middle of a row.
		outputDataIfBufferIsFull( );
		updataRowIndex( data, container );
		addDatatoCache( col, data );
		for ( int i = col + 1; i < col + span; i++ )
		{
			BlankData blankData = new BlankData( data );
			blankData.setType( Type.HORIZONTAL );
			addDatatoCache( i, blankData );
		}
		if ( data.getDataType( ) == SheetData.IMAGE )
		{
			addEmptyData( data, container );
		}

		if ( container instanceof XlsCell )
		{
			XlsCell cell = (XlsCell)container;
			data.setRowSpanInDesign( cell.getRowSpan( ) - 1 );
		}
	}

	protected void addEmptyData( SheetData data, XlsContainer container )
	{
		int parentStartCoordinate = container.getSizeInfo( )
				.getStartCoordinate( );
		int parentEndCoordinate = container.getSizeInfo( ).getEndCoordinate( );
		int childStartCoordinate = data.getSizeInfo( ).getStartCoordinate( );
		int childEndCoordinate = data.getSizeInfo( ).getEndCoordinate( );
		if ( childEndCoordinate < parentEndCoordinate )
		{
			StyleEntry style = container.getStyle( );
			removeLeftBorder( style );
			int column = axis.getColumnIndexByCoordinate( childEndCoordinate );
			Data empty = createEmptyData( style );
			empty.setSizeInfo( new ContainerSizeInfo( childEndCoordinate,
					parentEndCoordinate - childEndCoordinate ) );
			empty.setRowIndex( data.getRowIndex( ) );
			addDatatoCache( column, empty );
		}
		if ( childStartCoordinate > parentStartCoordinate )
		{
			StyleEntry style = container.getStyle( );
			removeRightBorder( style );
			int column = axis.getColumnIndexByCoordinate( childStartCoordinate );
			Data empty = createEmptyData( style );
			empty.setSizeInfo( new ContainerSizeInfo( childStartCoordinate,
					parentStartCoordinate - childStartCoordinate ) );
			empty.setRowIndex( data.getRowIndex( ) );
			addDatatoCache( column, empty );
		}
	}

	private void removeRightBorder( StyleEntry style )
	{
		style.setProperty( StyleConstant.BORDER_RIGHT_COLOR_PROP, null );
		style.setProperty( StyleConstant.BORDER_RIGHT_STYLE_PROP, null );
		style.setProperty( StyleConstant.BORDER_RIGHT_WIDTH_PROP, null );
	}

	private void removeLeftBorder( StyleEntry style )
	{
		style.setProperty( StyleConstant.BORDER_LEFT_COLOR_PROP, null );
		style.setProperty( StyleConstant.BORDER_LEFT_STYLE_PROP, null );
		style.setProperty( StyleConstant.BORDER_LEFT_WIDTH_PROP, null );
	}

	private void removeDiagonalLine( StyleEntry style )
	{
		style.setProperty( StyleConstant.BORDER_DIAGONAL_COLOR_PROP, null );
		style.setProperty( StyleConstant.BORDER_DIAGONAL_STYLE_PROP, null );
		style.setProperty( StyleConstant.BORDER_DIAGONAL_WIDTH_PROP, null );
		style.setProperty( StyleConstant.BORDER_ANTIDIAGONAL_COLOR_PROP, null );
		style.setProperty( StyleConstant.BORDER_ANTIDIAGONAL_STYLE_PROP, null );
		style.setProperty( StyleConstant.BORDER_ANTIDIAGONAL_WIDTH_PROP, null );
	}

	protected Data createEmptyData( StyleEntry style )
	{
		return createData( EMPTY, style, Data.STRING );
	}

	protected void updataRowIndex( SheetData data, XlsContainer container )
	{
		int rowIndex = container.getRowIndex( ) + 1;
		data.setRowIndex( rowIndex );
		container.setRowIndex( rowIndex );
	}

	private void outputDataIfBufferIsFull( )
	{
		if ( getCurrentContainer( ).getRowIndex( ) >= maxRow )
		{
			emitter.outputSheet( );
			cache.clearCachedSheetData( );
			resetContainers( );
		}
	}

	/**
	 * @param data
	 */
	private void applyTopBorderStyle( SheetData data )
	{
		XlsContainer container = getCurrentContainer( );
		int rowIndex = container.getRowIndex( );
		XlsContainer parent = container;
		while ( parent != null && parent.getStartRowId( ) == rowIndex )
		{
			StyleBuilder.applyTopBorder( parent.getStyle( ), data
					.getStyle( ) );
			parent = parent.getParent( );
		}
	}

	public XlsContainer createContainer( ContainerSizeInfo sizeInfo,
			IStyle style, XlsContainer parent )
	{
		return new XlsContainer( engine.createEntry( sizeInfo, style ),
				sizeInfo, parent );
	}

	public XlsContainer createCellContainer( IStyle style, XlsContainer parent, int rowSpan )
	{
		ContainerSizeInfo sizeInfo = parent.getSizeInfo( );
		return new XlsCell( engine.createEntry( sizeInfo, style ), sizeInfo,
				parent, rowSpan );
	}

	public Map<StyleEntry,Integer> getStyleMap( )
	{
		return engine.getStyleIDMap( );
	}

	// TODO: style ranges.
	// public List<ExcelRange> getStyleRanges( )
	// {
	// return engine.getStyleRanges( );
	// }

	public int[] getCoordinates( )
	{
		int[] coord = axis.getColumnWidths( );

		if ( coord.length <= maxCol )
		{
			return coord;
		}
		else
		{
			int[] ncoord = new int[maxCol];
			System.arraycopy( coord, 0, ncoord, 0, maxCol );
			return ncoord;
		}
	}

	public int getRowCount( )
	{
		return cache.getMaxRow( );
	}

	public AxisProcessor getAxis( )
	{
		return axis;
	}

	public SheetData getColumnLastData( int column )
	{
		return cache.getColumnLastData( column );
	}

	private void addDatatoCache( int col, SheetData value )
	{
		cache.addData( col, value );
	}

	public void complete( boolean isAuto )
	{
		engine.applyContainerBottomStyle( containers.get( 0 ) );
		Iterator<SheetData[]> iterator = cache.getRowIterator( );
		while ( iterator.hasNext( ) )
		{
			SheetData[] rowData = iterator.next( );

			for ( int j = 0; j < rowData.length; j++ )
			{
				SheetData data = rowData[j];
				if ( data == null || data.isBlank( ) )
				{
					continue;
				}

				ContainerSizeInfo rule = data.getSizeInfo( );

				// Excel Cell Starts From 1
				int start = axis.getColumnIndexByCoordinate( rule
						.getStartCoordinate( ) ) + 1;
				int end = axis.getColumnIndexByCoordinate( rule
						.getEndCoordinate( ) ) + 1;

				end = Math.min( end, maxCol );
				int scount = Math.max( 0, end - start - 1 );
				// Excel Span Starts From 1
				Span span = new Span( start, scount );
				data.setSpan( span );

				HyperlinkDef hyperLink = data.getHyperlinkDef( );
				if ( hyperLink != null )
				{
					if ( hyperLink.getType( ) == IHyperlinkAction.ACTION_BOOKMARK )
					{
						setLinkedBookmark( data, hyperLink );
					}
				}
				int styleid = engine.getStyleID( data );
				data.setStyleId( styleid );
			}
			calculateRowHeight( rowData, isAuto );
		}
	}

	/**
	 * @param data
	 * @param hyperLink
	 */
	private void setLinkedBookmark( SheetData data, HyperlinkDef hyperLink )
	{
		String bookmarkName = hyperLink.getUrl( );
		BookmarkDef linkedBookmark = bookmarkList
				.get( bookmarkName );
		if ( linkedBookmark != null )
		{
			data.setLinkedBookmark( linkedBookmark );
		}
		else
		{
			BookmarkDef newBookmark;
			if ( ExcelUtil.isValidBookmarkName( bookmarkName ) )
				newBookmark = new BookmarkDef( bookmarkName );
			else
			{
				String generateBookmarkName = getGenerateBookmark( bookmarkName );
				newBookmark = new BookmarkDef(
						generateBookmarkName );
				cachedBookmarks.put( bookmarkName,
						generateBookmarkName );
			}
			data.setLinkedBookmark( newBookmark );
		}
	}

	public Stack<XlsTable> getTable( )
	{
		return tables;
	}

	public void addContainerStyle( IStyle computedStyle )
	{
		engine.addContainderStyle(computedStyle);	
	}

	public void removeContainerStyle( )
	{
		engine.removeForeignContainerStyle( );
	}

	public void resetContainers( )
	{
		for ( XlsContainer container : containers )
		{
			container.setRowIndex( 0 );
			container.setStartRowId( 0 );
		}
		for ( XlsTable table : tables )
		{
			table.setRowIndex( 0 );
		}
	}

	public ExcelLayoutEngineIterator getIterator( )
	{
		return new ExcelLayoutEngineIterator( );
	}

	private class ExcelLayoutEngineIterator implements Iterator<RowData>
	{

		Iterator<SheetData[]> rowIterator;

		public ExcelLayoutEngineIterator( )
		{
			rowIterator = cache.getRowIterator( );
		}

		public boolean hasNext( )
		{
			return rowIterator.hasNext( );
		}

		public RowData next( )
		{
			SheetData[] row = rowIterator.next( );
			List<SheetData> data = new ArrayList<SheetData>( );
			int width = Math.min( row.length, maxCol - 1 );
			int rowIndex = 0;
			for ( int i = 0; i < width; i++ )
			{
				SheetData d = row[i];
				if ( d == null || d.isBlank( ) )
				{
					continue;
				}
				if ( d.isProcessed( ) )
				{
					continue;
				}
				rowIndex = d.getRowIndex( );
				d.setProcessed( true );
				data.add( row[i] );
			}
			SheetData[] rowdata = new SheetData[data.size( )];
			double rowHeight = Math.max( DEFAULT_ROW_HEIGHT, cache
					.getRowHeight( rowIndex - 1 ) );
			data.toArray( rowdata );
			return new RowData( rowdata, rowHeight );
		}

		public void remove( )
		{
			throw new UnsupportedOperationException( );
		}
	}

	public void cacheBookmarks( int sheetIndex )
	{
		List<BookmarkDef> currentSheetBookmarks = cache.getBookmarks( );
		for ( BookmarkDef book : currentSheetBookmarks )
		{
			book.setSheetIndex( sheetIndex );
			bookmarkList.put( book.getName( ), book );
		}
	}

	public HashMap<String, BookmarkDef> getAllBookmarks( )
	{
		return this.bookmarkList;
	}

	/**
	 * @param bookmarkName
	 * @return generatedBookmarkName
	 */
	public String getGenerateBookmark( String bookmarkName )
	{
		String generatedName = cachedBookmarks.get( bookmarkName );
		return generatedName != null ? generatedName : AUTO_GENERATED_BOOKMARK
				+ autoBookmarkIndex++;
	}

	protected Data createData( )
	{
		return new Data( );
	}

	private Data createData( Object text, StyleEntry style, int type )
	{
		return createData( text, style, type, 0 );
	}

	protected Data createData( Object text, StyleEntry s, int dataType,
			int rowSpanOfDesign )
	{
		Data data = createData( );
		data.setValue( text );
		data.setStyle( s );
		data.setDataType( dataType );
		data.setRowSpanInDesign( rowSpanOfDesign );
		return data;
	}
}