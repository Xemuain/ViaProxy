/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viaproxy.ui.impl;

import com.google.gson.*;
import net.lenni0451.commons.swing.GBC;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.saves.impl.AccountsSave;
import net.raphimc.viaproxy.saves.impl.accounts.Account;
import net.raphimc.viaproxy.saves.impl.accounts.BedrockAccount;
import net.raphimc.viaproxy.saves.impl.accounts.MicrosoftAccount;
import net.raphimc.viaproxy.ui.I18n;
import net.raphimc.viaproxy.ui.UITab;
import net.raphimc.viaproxy.ui.ViaProxyWindow;
import net.raphimc.viaproxy.ui.popups.AddAccountPopup;
import net.raphimc.viaproxy.util.TFunction;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static net.raphimc.viaproxy.ui.ViaProxyWindow.BODY_BLOCK_PADDING;
import static net.raphimc.viaproxy.ui.ViaProxyWindow.BORDER_PADDING;

public class AccountsTab extends UITab {

    private JList<Account> accountsList;
    private JButton addMicrosoftAccountButton;
    private JButton addBedrockAccountButton;

    private AddAccountPopup addAccountPopup;
    private Thread addThread;

    public AccountsTab(final ViaProxyWindow frame) {
        super(frame, "accounts");
    }

    @Override
    protected void init(JPanel contentPane) {
        JPanel body = new JPanel();
        body.setLayout(new GridBagLayout());

        int gridy = 0;
        {
            JLabel infoLabel = new JLabel("<html><p>" + I18n.get("tab.accounts.description.line1") + "</p></html>");
            GBC.create(body).grid(0, gridy++).weightx(1).insets(BORDER_PADDING, BORDER_PADDING, 0, BORDER_PADDING).fill(GBC.HORIZONTAL).add(infoLabel);
        }
        {
            JScrollPane scrollPane = new JScrollPane();
            DefaultListModel<Account> model = new DefaultListModel<>();
            this.accountsList = new JList<>(model);
            this.accountsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            this.accountsList.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        int row = AccountsTab.this.accountsList.locationToIndex(e.getPoint());
                        if (!accountsList.isSelectedIndex(row))
                            AccountsTab.this.accountsList.setSelectedIndex(row);
                    } else if (e.getClickCount() == 2) {
                        int index = AccountsTab.this.accountsList.getSelectedIndex();
                        if (index != -1) AccountsTab.this.markSelected(index);
                    }
                }
            });
            this.accountsList.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int index = AccountsTab.this.accountsList.getSelectedIndex();
                    if (index == -1) return;

                    boolean ctrl = (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0;

                    if (e.getKeyCode() == KeyEvent.VK_UP && ctrl) {
                        AccountsTab.this.moveUp(index);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN && ctrl) {
                        AccountsTab.this.moveDown(index);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        AccountsTab.this.markSelected(index);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                        removeSelected();
                        e.consume();
                    }
                }
            });
            this.accountsList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    DefaultListCellRenderer component = (DefaultListCellRenderer) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    Account account = (Account) value;
                    if (ViaProxy.getConfig().getAccount() == account) {
                        component.setText("<html><span style=\"color:rgb(0, 180, 0)\"><b>" + account.getDisplayString() + "</b></span></html>");
                    } else {
                        component.setText(account.getDisplayString());
                    }
                    return component;
                }
            });
            scrollPane.setViewportView(this.accountsList);
            JPopupMenu contextMenu = new JPopupMenu();
            {
                JMenuItem selectItem = new JMenuItem(I18n.get("tab.accounts.list.context_menu.select"));
                selectItem.addActionListener(event -> {
                    int index = this.accountsList.getSelectedIndex();
                    if (index != -1) this.markSelected(index);
                });
                contextMenu.add(selectItem);
            }
            {
                JMenuItem removeItem = new JMenuItem(I18n.get("tab.accounts.list.context_menu.remove"));
                removeItem.addActionListener(event -> removeSelected());
                contextMenu.add(removeItem);
            }
            {
                JMenuItem moveUp = new JMenuItem(I18n.get("tab.accounts.list.context_menu.move_up"));
                moveUp.addActionListener(event -> {
                    int index = this.accountsList.getSelectedIndex();
                    if (index != -1) this.moveUp(index);
                });
                contextMenu.add(moveUp);
            }
            {
                JMenuItem moveDown = new JMenuItem(I18n.get("tab.accounts.list.context_menu.move_down"));
                moveDown.addActionListener(event -> {
                    int index = this.accountsList.getSelectedIndex();
                    if (index != -1) this.moveDown(index);
                });
                contextMenu.add(moveDown);
            }
            {
                JMenuItem importItem = new JMenuItem(I18n.get("tab.accounts.list.context_menu.import"));
                importItem.addActionListener(event -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
                    chooser.setAcceptAllFileFilterUsed(false);
                    if (chooser.showOpenDialog(this.viaProxyWindow) == JFileChooser.APPROVE_OPTION) {
                        File file = chooser.getSelectedFile();
                        if (!file.getName().toLowerCase().endsWith(".json"))
                            return;

                        try (Reader reader = new FileReader(file)) {
                            JsonElement element = JsonParser.parseReader(reader);
                            if (!element.isJsonArray())
                                throw new IllegalStateException("Invalid file (expected JSON array)");

                            List<AccountsSave.Entry> loadedEntries = ViaProxy.getSaveManager().accountsSave.loadFromJsonArray(element.getAsJsonArray());
                            for (AccountsSave.Entry entry : loadedEntries) {
                                ViaProxy.getSaveManager().accountsSave.addEntry(entry);

                                if (entry instanceof AccountsSave.KnownEntry knownEntry)
                                    this.addAccount(knownEntry.account());
                            }

                            ViaProxy.getSaveManager().save();
                        } catch (IOException ex) {
                            ViaProxyWindow.showException(ex);
                        }
                    }
                });

                JMenuItem exportItem = new JMenuItem(I18n.get("tab.accounts.list.context_menu.export"));
                exportItem.addActionListener(event -> {
                    List<Account> selected = this.accountsList.getSelectedValuesList();
                    if (selected.isEmpty()) return;

                    JFileChooser chooser = new JFileChooser();
                    chooser.setSelectedFile(new File("viaproxy_accounts.json"));
                    if (chooser.showSaveDialog(this.viaProxyWindow) == JFileChooser.APPROVE_OPTION) {
                        File file = chooser.getSelectedFile();

                        try (Writer writer = new FileWriter(file)) {
                            Gson gson = new GsonBuilder().setPrettyPrinting().create();

                            JsonArray array = new JsonArray();
                            for (Account account : selected) {
                                JsonObject json = account.toJson();
                                json.addProperty("accountType", account.getClass().getName());
                                array.add(json);
                            }

                            gson.toJson(array, writer);
                        } catch (IOException ex) {
                            ViaProxyWindow.showException(ex);
                        }
                    }
                });

                contextMenu.addSeparator();
                contextMenu.add(importItem);
                contextMenu.add(exportItem);
            }
            this.accountsList.setComponentPopupMenu(contextMenu);
            GBC.create(body).grid(0, gridy++).weight(1, 1).insets(BODY_BLOCK_PADDING, BORDER_PADDING, 0, BORDER_PADDING).fill(GBC.BOTH).add(scrollPane);
        }
        {
            final JPanel addButtons = new JPanel();
            addButtons.setLayout(new GridLayout(1, 3, BORDER_PADDING, 0));
            {
                JButton addOfflineAccountButton = new JButton(I18n.get("tab.accounts.add_offline.label"));
                addOfflineAccountButton.addActionListener(event -> {
                    String username = JOptionPane.showInputDialog(this.viaProxyWindow, I18n.get("tab.accounts.add_offline.enter_username"), I18n.get("tab.accounts.add.title"), JOptionPane.PLAIN_MESSAGE);
                    if (username != null && !username.trim().isEmpty()) {
                        Account account = ViaProxy.getSaveManager().accountsSave.addAccount(username);
                        ViaProxy.getSaveManager().save();
                        this.addAccount(account);
                    }
                });
                addButtons.add(addOfflineAccountButton);
            }
            {
                this.addMicrosoftAccountButton = new JButton(I18n.get("tab.accounts.add_microsoft.label"));
                this.addMicrosoftAccountButton.addActionListener(event -> {
                    this.addMicrosoftAccountButton.setEnabled(false);
                    this.handleLogin(msaDeviceCodeConsumer -> new MicrosoftAccount(JavaAuthManager.create(MinecraftAuth.createHttpClient()).login(DeviceCodeMsaAuthService::new, msaDeviceCodeConsumer)));
                });
                addButtons.add(this.addMicrosoftAccountButton);
            }
            {
                this.addBedrockAccountButton = new JButton(I18n.get("tab.accounts.add_bedrock.label"));
                this.addBedrockAccountButton.addActionListener(event -> {
                    this.addBedrockAccountButton.setEnabled(false);
                    this.handleLogin(msaDeviceCodeConsumer -> new BedrockAccount(BedrockAuthManager.create(MinecraftAuth.createHttpClient(), ProtocolConstants.BEDROCK_VERSION_NAME).login(DeviceCodeMsaAuthService::new, msaDeviceCodeConsumer)));
                });
                addButtons.add(this.addBedrockAccountButton);
            }

            JPanel border = new JPanel();
            border.setLayout(new GridBagLayout());
            border.setBorder(BorderFactory.createTitledBorder(I18n.get("tab.accounts.add.title")));
            GBC.create(border).grid(0, 0).weightx(1).insets(2, 4, 4, 4).fill(GBC.HORIZONTAL).add(addButtons);

            GBC.create(body).grid(0, gridy++).weightx(1).insets(BODY_BLOCK_PADDING, BORDER_PADDING, BORDER_PADDING, BORDER_PADDING).fill(GBC.HORIZONTAL).add(border);
        }

        contentPane.setLayout(new BorderLayout());
        contentPane.add(body, BorderLayout.CENTER);

        ViaProxy.getSaveManager().accountsSave.getAccounts().forEach(this::addAccount);
        DefaultListModel<Account> model = (DefaultListModel<Account>) this.accountsList.getModel();
        if (!model.isEmpty()) this.markSelected(0);
    }

    private void closePopup() { // Might be getting called multiple times
        if (this.addAccountPopup != null) {
            this.addAccountPopup.markExternalClose();
            this.addAccountPopup.setVisible(false);
            this.addAccountPopup.dispose();
            this.addAccountPopup = null;
        }
        this.addMicrosoftAccountButton.setEnabled(true);
        this.addBedrockAccountButton.setEnabled(true);
    }

    private void addAccount(final Account account) {
        DefaultListModel<Account> model = (DefaultListModel<Account>) this.accountsList.getModel();
        model.addElement(account);
    }

    public void markSelected(final int index) {
        if (index < 0 || index >= this.accountsList.getModel().getSize()) {
            ViaProxy.getConfig().setAccount(null);
            return;
        }

        ViaProxy.getConfig().setAccount(ViaProxy.getSaveManager().accountsSave.getAccounts().get(index));
        this.accountsList.repaint();
    }

    private void moveUp(final int index) {
        DefaultListModel<Account> model = (DefaultListModel<Account>) this.accountsList.getModel();
        if (model.getSize() == 0) return;
        if (index == 0) return;

        Account account = model.remove(index);
        model.add(index - 1, account);
        this.accountsList.setSelectedIndex(index - 1);

        ViaProxy.getSaveManager().accountsSave.removeAccount(account);
        ViaProxy.getSaveManager().accountsSave.addAccount(index - 1, account);
        ViaProxy.getSaveManager().save();
    }

    private void moveDown(final int index) {
        DefaultListModel<Account> model = (DefaultListModel<Account>) this.accountsList.getModel();
        if (model.getSize() == 0) return;
        if (index == model.getSize() - 1) return;

        Account account = model.remove(index);
        model.add(index + 1, account);
        this.accountsList.setSelectedIndex(index + 1);

        ViaProxy.getSaveManager().accountsSave.removeAccount(account);
        ViaProxy.getSaveManager().accountsSave.addAccount(index + 1, account);
        ViaProxy.getSaveManager().save();
    }

    private void handleLogin(final TFunction<Consumer<MsaDeviceCode>, Account> requestHandler) {
        this.addThread = new Thread(() -> {
            try {
                final Account account = requestHandler.apply(msaDeviceCode -> SwingUtilities.invokeLater(() -> new AddAccountPopup(this.viaProxyWindow, msaDeviceCode, popup -> this.addAccountPopup = popup, () -> {
                    this.closePopup();
                    this.addThread.interrupt();
                })));
                SwingUtilities.invokeLater(() -> {
                    this.closePopup();
                    ViaProxy.getSaveManager().accountsSave.addAccount(account);
                    ViaProxy.getSaveManager().save();
                    this.addAccount(account);
                    ViaProxyWindow.showInfo(I18n.get("tab.accounts.add.success", account.getName()));
                });
            } catch (InterruptedException ignored) {
            } catch (TimeoutException e) {
                SwingUtilities.invokeLater(() -> {
                    this.closePopup();
                    ViaProxyWindow.showError(I18n.get("tab.accounts.add.timeout", "300"));
                });
            } catch (Throwable t) {
                if (t.getCause() instanceof InterruptedException) {
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    this.closePopup();
                    ViaProxyWindow.showException(t);
                });
            }
        }, "Add Account Thread");
        this.addThread.setDaemon(true);
        this.addThread.start();
    }

    private void removeSelected() {
        List<Account> selected = this.accountsList.getSelectedValuesList();
        if (selected.isEmpty()) return;

        DefaultListModel<String> previewModel = new DefaultListModel<>();
        selected.forEach(account -> previewModel.addElement(account.getDisplayString()));

        JList<String> previewList = new JList<>(previewModel);
        previewList.setEnabled(true);
        previewList.setVisibleRowCount(Math.min(10, previewModel.size()));

        JScrollPane scrollPane = new JScrollPane(previewList);
        scrollPane.setPreferredSize(new Dimension(300, 150));

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel(I18n.get("tab.accounts.confirm_delete", String.valueOf(previewModel.size()))), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this.viaProxyWindow, panel, I18n.get("tab.accounts.confirm_delete.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
            return;

        DefaultListModel<Account> model1 = (DefaultListModel<Account>) this.accountsList.getModel();
        for (Account account : selected) {
            model1.removeElement(account);
            ViaProxy.getSaveManager().accountsSave.removeAccount(account);

            if (ViaProxy.getConfig().getAccount() == account)
                this.markSelected(-1);
        }

        ViaProxy.getSaveManager().save();

        if (!model1.isEmpty()) {
            this.accountsList.setSelectedIndex(0);
        } else {
            this.accountsList.setSelectedIndex(-1);
        }
    }
}
