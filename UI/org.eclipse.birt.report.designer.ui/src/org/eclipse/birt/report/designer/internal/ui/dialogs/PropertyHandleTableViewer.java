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

package org.eclipse.birt.report.designer.internal.ui.dialogs;

import org.eclipse.birt.report.designer.internal.ui.util.ExceptionHandler;
import org.eclipse.birt.report.model.activity.SemanticException;
import org.eclipse.birt.report.model.api.PropertyHandle;
import org.eclipse.birt.report.model.metadata.PropertyValueException;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;


/**
 * TODO: Please document
 * 
 * @version $Revision$ $Date$
 */
public final class PropertyHandleTableViewer
{
    private TableViewer viewer;
    private Composite mainControl;
    private Button btnRemove;
    private Button btnUp;
    private Button btnDown;
    private MenuItem itmRemove;
    private MenuItem itmRemoveAll;
    
    /**
     * 
     */
    public PropertyHandleTableViewer(Composite parent, boolean showMenus, boolean showButtons, boolean enableKeyStrokes)
    {
        mainControl = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        mainControl.setLayout(layout);

        GridData data = null;
        viewer = new TableViewer(mainControl, SWT.FULL_SELECTION);
        data = new GridData(GridData.FILL_BOTH);
        viewer.getControl().setLayoutData(data);
        
        viewer.getTable( ).setHeaderVisible( true );
        viewer.getTable( ).setLinesVisible( true );

        if(showButtons)
        {
            Composite btnComposite = new Composite(mainControl, SWT.NONE);
            data = new GridData();
            data.verticalAlignment = SWT.CENTER;
            btnComposite.setLayoutData(data);
            GridLayout btnLayout = new GridLayout();
            layout.verticalSpacing = 20;
            btnComposite.setLayout(btnLayout);
            
            GridData btnData = new GridData(GridData.CENTER);
            btnData.widthHint = 20;
            btnData.heightHint = 20;
//            btnData.minimumHeight = 20;
//            btnData.minimumWidth = 20;
            btnUp = new Button(btnComposite, SWT.ARROW | SWT.UP);
            btnUp.setLayoutData(btnData);
            btnUp.addSelectionListener(new SelectionListener(){

                public void widgetSelected(SelectionEvent e)
                {
                    //Get the current selection and delete that row
                    int index = viewer.getTable( ).getSelectionIndex( );
                    PropertyHandle handle = (PropertyHandle) viewer.getInput();
                    if ( index > 0
                            && handle.getListValue( ) != null
                            && index < handle.getListValue( ).size( ))
                    {
                        viewer.cancelEditing();
                        try
                        {
                            handle.moveItem( index, index -1 );
                        }
                        catch ( PropertyValueException e1 )
                        {
                            ExceptionHandler.handle( e1 );
                        }
                        viewer.refresh( );
                        viewer.getTable( ).select( index - 1);
                    }

                }

                public void widgetDefaultSelected(SelectionEvent e)
                {
                }
                
            });
            
            btnRemove = new Button(btnComposite, SWT.PUSH);
            btnRemove.setImage(PlatformUI.getWorkbench( ).getSharedImages( ).getImage( ISharedImages.IMG_TOOL_DELETE ));
            btnRemove.setLayoutData(btnData);
            btnRemove.addSelectionListener(new SelectionListener(){

                public void widgetSelected(SelectionEvent e)
                {
                    removeSelectedItem();
                }

                public void widgetDefaultSelected(SelectionEvent e)
                {
                }
                
            });
            
            btnDown = new Button(btnComposite, SWT.ARROW | SWT.DOWN);
            btnDown.setLayoutData(btnData);
            btnDown.addSelectionListener(new SelectionListener(){

                public void widgetSelected(SelectionEvent e)
                {
                    //Get the current selection and delete that row
                    int index = viewer.getTable( ).getSelectionIndex( );
                    PropertyHandle handle = (PropertyHandle) viewer.getInput();
                    if ( index > -1
                            && handle.getListValue( ) != null
                            && index < handle.getListValue( ).size( ) - 1)
                    {
                        viewer.cancelEditing();
                        try
                        {
                            handle.moveItem( index, index + 2 );
                        }
                        catch ( PropertyValueException e1 )
                        {
                            ExceptionHandler.handle( e1 );
                        }
                        viewer.refresh( );
                        viewer.getTable( ).select( index + 1);
                    }
                }

                public void widgetDefaultSelected(SelectionEvent e)
                {
                }
                
            });
        }
        
        if(showMenus)
        {
            Menu menu = new Menu(viewer.getTable());
            menu.addMenuListener(new MenuAdapter(){
                public void menuShown(MenuEvent e)
                {
                    viewer.cancelEditing();
                }
            });
            itmRemove = new MenuItem(menu, SWT.NONE);
            itmRemove .setText("Remove");
            itmRemove .addSelectionListener(new SelectionAdapter(){

                public void widgetSelected(SelectionEvent e)
                {
                    removeSelectedItem();
                }

            });
            itmRemoveAll = new MenuItem(menu, SWT.NONE);
            itmRemoveAll.setText("Remove All");
            itmRemoveAll.addSelectionListener(new SelectionAdapter(){
                public void widgetSelected(SelectionEvent e)
                {
                    try
                    {
                        PropertyHandle handle = (PropertyHandle) viewer.getInput();
                        handle.clearValue();
                        viewer.refresh();
                    }
                    catch (SemanticException e1)
                    {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }
            });

            viewer.getTable().setMenu(menu);
        }
        
        if(enableKeyStrokes)
        {
            viewer.getTable().addKeyListener(new KeyListener(){

                public void keyPressed(KeyEvent e)
                {
                }

                public void keyReleased(KeyEvent e)
                {
                    if ( e.keyCode == SWT.DEL )
                    {
                        removeSelectedItem();
                    }
                }
                
            });
        }
    }
    
    public TableViewer getViewer()
    {
        return viewer;
    }
    
    public Composite getControl()
    {
        return mainControl;
    }
    
    public Button getUpButton()
    {
        return btnUp;
    }
    
    public Button getDownButton()
    {
        return btnDown;
    }
    
    public Button getRemoveButton()
    {
        return btnRemove;
    }
    
    public MenuItem getRemoveMenuItem()
    {
        return itmRemove;
    }
    
    public MenuItem getRemoveAllMenuItem()
    {
        return itmRemoveAll;
    }
    
    private final void removeSelectedItem()
    {
        int index = viewer.getTable( ).getSelectionIndex( );
        PropertyHandle handle = (PropertyHandle) viewer.getInput();
        int count = (handle.getListValue() == null)?0:handle.getListValue().size(); 
        //Do not allow deletion of the last item.
        if ( index > -1 && index < count )
        {
            try
            {
                handle.removeItem( index );
            }
            catch ( PropertyValueException e1 )
            {
                ExceptionHandler.handle( e1 );
            }
            viewer.refresh( );
            viewer.getTable( ).select( index );
        }
    }
}
