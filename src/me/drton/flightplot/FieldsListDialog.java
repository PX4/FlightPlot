package me.drton.flightplot;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

public class FieldsListDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonAdd;
    private JTable fieldsTable;
    private JButton buttonClose;
    private JTextField textSearch;
    private DefaultTableModel fieldsTableModel;
    private TableRowSorter sorter;

    public FieldsListDialog(final Runnable callbackAdd) {
        setContentPane(contentPane);
        setModal(false);
        setTitle("Fields List");
        getRootPane().setDefaultButton(buttonAdd);
        buttonAdd.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                callbackAdd.run();
            }
        });
        buttonClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onClose();
            }
        });
        // call onClose() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        // call onClose() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onClose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        fieldsTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (me.getClickCount() == 2) {
                    callbackAdd.run();
                }
            }
        });
        textSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                filterFields(textSearch.getText());
            }
            public void removeUpdate(DocumentEvent e) {
                filterFields(textSearch.getText());
            }
            public void insertUpdate(DocumentEvent e) {
                filterFields(textSearch.getText());
            }
        });
    }

    private void onClose() {
        setVisible(false);
    }

    @Override
    public void setVisible(boolean value) {
        super.setVisible(value);
        // Focus search input on showing
        textSearch.requestFocus();
    }

    public void setFieldsList(Map<String, String> fields) {
        while (fieldsTableModel.getRowCount() > 0) {
            fieldsTableModel.removeRow(0);
        }
        List<String> fieldsList = new ArrayList<String>(fields.keySet());
        Collections.sort(fieldsList);
        for (String field : fieldsList) {
            fieldsTableModel.addRow(new Object[]{field, fields.get(field)});
        }
    }

    private void filterFields(String str) {
        RowFilter<DefaultTableModel, Object> rf = RowFilter.regexFilter("(?i)"  + Pattern.quote(str), 0);
        sorter.setRowFilter(rf);
    }

    public List<String> getSelectedFields() {
        List<String> selectedFields = new ArrayList<String>();
        for (int i : fieldsTable.getSelectedRows()) {
            selectedFields.add((String) fieldsTable.getValueAt(i, 0));
        }
        return selectedFields;
    }

    private void createUIComponents() {
        // Fields table
        fieldsTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        fieldsTableModel.addColumn("Field");
        fieldsTableModel.addColumn("Type");
        fieldsTable = new JTable(fieldsTableModel);
        sorter = new TableRowSorter<DefaultTableModel>(fieldsTableModel);
        fieldsTable.setRowSorter(sorter);
    }
}
