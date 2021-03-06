/*******************************************************************************
 * Copyright (c) 2013 hangum.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     nilriri - initial API and implementation
 ******************************************************************************/
package com.hangum.tadpole.rdb.core.viewers.object.sub.rdb.table;

import java.sql.PreparedStatement;

import org.apache.log4j.Logger;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;

import com.hangum.tadpole.commons.sql.TadpoleSQLManager;
import com.hangum.tadpole.commons.sql.define.DBDefine;
import com.hangum.tadpole.dao.mysql.TableColumnDAO;
import com.hangum.tadpole.dao.mysql.TableDAO;
import com.hangum.tadpole.dao.system.UserDBDAO;
import com.ibatis.sqlmap.client.SqlMapClient;

/**
 * column comment editor
 * 
 * @author nilriri
 *
 */
public class ColumnCommentEditorSupport extends EditingSupport {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6292003867430114514L;

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(ColumnCommentEditorSupport.class);

	private final TableViewer tableviewer;
	private final TableViewer viewer;
	private UserDBDAO userDB;
	private int column;

	/**
	 * 
	 * @param viewer
	 * @param explorer
	 */
	public ColumnCommentEditorSupport(TableViewer tableviewer, TableViewer viewer, UserDBDAO userDB, int column) {
		super(viewer);
		
		this.tableviewer = tableviewer;
		this.viewer = viewer;
		this.userDB = userDB;
		this.column = column;
	}

	@Override
	protected CellEditor getCellEditor(Object element) {
		if(column == 3) return new TextCellEditor(viewer.getTable());
		else return null;
	}

	@Override
	protected boolean canEdit(Object element) {
		if(column == 3) {
			logger.debug("DBMS Type is " + DBDefine.getDBDefine(userDB.getDbms_types()));
			if (DBDefine.getDBDefine(userDB.getDbms_types()) == DBDefine.ORACLE_DEFAULT || 
					DBDefine.getDBDefine(userDB.getDbms_types()) == DBDefine.MSSQL_DEFAULT ||
					DBDefine.getDBDefine(userDB.getDbms_types()) == DBDefine.MSSQL_8_LE_DEFAULT ) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	protected Object getValue(Object element) {
		try {
			TableColumnDAO dao = (TableColumnDAO) element;
			String comment = dao.getComment();
			return comment == null ? "" : comment;
		} catch (Exception e) {
			logger.error("getValue error ", e);
			return "";
		}
	}

	@Override
	protected void setValue(Object element, Object value) {
		String comment = "";
		try {
			logger.debug("element.getClass().toString() is " + element.getClass().toString());

			TableColumnDAO dao = (TableColumnDAO) element;

			comment = (String) (value == null ? "" : value);
			
			logger.debug("dao column name is " + dao.getField());
			
			// 기존 코멘트와 다를때만 db에 반영한다.
			if (!(comment.equals(dao.getComment()))) {
				dao.setComment(comment);
				ApplyComment(dao);
			}

			viewer.update(element, null);
		} catch (Exception e) {
			logger.error("setValue error ", e);
		}
		viewer.update(element, null);
	}

	private void ApplyComment(TableColumnDAO dao) {
		// TODO : DBMS별 처리를 위해 별도의 Class로 분리해야 하지 않을까? 

		java.sql.Connection javaConn = null;
		PreparedStatement stmt = null;
		try {

			logger.debug("userDB is " + userDB.toString());

			SqlMapClient client = TadpoleSQLManager.getInstance(userDB);

			javaConn = client.getDataSource().getConnection();

			IStructuredSelection is = (IStructuredSelection) tableviewer.getSelection();
			
			TableDAO tableDAO = (TableDAO)is.getFirstElement();

			StringBuffer query = new StringBuffer();

			if (DBDefine.getDBDefine(userDB.getDbms_types()) == DBDefine.ORACLE_DEFAULT) {
				
				query.append(" COMMENT ON COLUMN ").append(tableDAO.getName()+".").append(dao.getField()).append(" IS '").append(dao.getComment()).append("'");

				logger.debug("query is " + query.toString());
				
				stmt = javaConn.prepareStatement(query.toString());
				stmt.executeQuery();

			} else if (DBDefine.getDBDefine(userDB.getDbms_types()) == DBDefine.MSSQL_8_LE_DEFAULT) {
				query.append(" exec sp_dropextendedproperty 'Caption' ").append(", 'user' ,").append(userDB.getUsers());
				query.append(",'table' , '").append(tableDAO.getName()).append("'");
				query.append(",'column' , '").append(dao.getField()).append("'");
				stmt = javaConn.prepareStatement(query.toString());
				try {
					stmt.execute();
				} catch (Exception e) {
					logger.debug("query is " + query.toString());
					logger.error("Comment drop error ", e);
				}

				try {
					query = new StringBuffer();
					query.append(" exec sp_addextendedproperty 'Caption', '").append(dao.getComment()).append("' ,'user' ,").append(userDB.getUsers());
					query.append(",'table' , '").append(tableDAO.getName()).append("'");
					query.append(",'column', '").append(dao.getField()).append("'");
					stmt = javaConn.prepareStatement(query.toString());
					stmt.execute();
				} catch (Exception e) {
					logger.debug("query is " + query.toString());
					logger.error("Comment add error ", e);
				}
			} else if (DBDefine.getDBDefine(userDB.getDbms_types()) == DBDefine.MSSQL_DEFAULT ) {
				query.append(" exec sp_dropextendedproperty 'Caption' ").append(", 'user' , dbo ");
				query.append(",'table' , '").append(tableDAO.getName()).append("'");
				query.append(",'column' , '").append(dao.getField()).append("'");
				stmt = javaConn.prepareStatement(query.toString());
				try {
					stmt.execute();
				} catch (Exception e) {
					logger.debug("query is " + query.toString());
					logger.error("Comment drop error ", e);
				}

				try {
					query = new StringBuffer();
					query.append(" exec sp_addextendedproperty 'Caption', '").append(dao.getComment()).append("' ,'user' , dbo ");
					query.append(",'table' , '").append(tableDAO.getName()).append("'");
					query.append(",'column', '").append(dao.getField()).append("'");
					stmt = javaConn.prepareStatement(query.toString());
					stmt.execute();
				} catch (Exception e) {
					logger.debug("query is " + query.toString());
					logger.error("Comment add error ", e);
				}
			}

		} catch (Exception e) {
			logger.error("Comment change error ", e);
		} finally {
			try {
				stmt.close();
			} catch (Exception e) {
			}
			try {
				javaConn.close();
			} catch (Exception e) {
			}
		}
	}

}
