/*
 * This file is part of the dSploit.
 *
 * Copyleft of Simone Margaritelli aka evilsocket <evilsocket@gmail.com>
 *
 * dSploit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dSploit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with dSploit.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.evilsocket.dsploit;

import static it.evilsocket.dsploit.core.UpdateChecker.AVAILABLE_VERSION;
import static it.evilsocket.dsploit.core.UpdateChecker.GENTOO_AVAILABLE;
import static it.evilsocket.dsploit.core.UpdateChecker.UPDATE_AVAILABLE;
import static it.evilsocket.dsploit.core.UpdateChecker.UPDATE_CHECKING;
import static it.evilsocket.dsploit.core.UpdateChecker.UPDATE_NOT_AVAILABLE;
import static it.evilsocket.dsploit.net.NetworkDiscovery.ENDPOINT_ADDRESS;
import static it.evilsocket.dsploit.net.NetworkDiscovery.ENDPOINT_HARDWARE;
import static it.evilsocket.dsploit.net.NetworkDiscovery.ENDPOINT_NAME;
import static it.evilsocket.dsploit.net.NetworkDiscovery.ENDPOINT_UPDATE;
import static it.evilsocket.dsploit.net.NetworkDiscovery.NEW_ENDPOINT;

import it.evilsocket.dsploit.core.Logger;
import it.evilsocket.dsploit.core.ManagedReceiver;
import it.evilsocket.dsploit.core.MultiAttackService;
import it.evilsocket.dsploit.core.Plugin;
import it.evilsocket.dsploit.core.Shell;
import it.evilsocket.dsploit.core.System;
import it.evilsocket.dsploit.core.ToolsInstaller;
import it.evilsocket.dsploit.core.UpdateChecker;
import it.evilsocket.dsploit.core.UpdateService;
import it.evilsocket.dsploit.gui.dialogs.AboutDialog;
import it.evilsocket.dsploit.gui.dialogs.ChangelogDialog;
import it.evilsocket.dsploit.gui.dialogs.ConfirmDialog;
import it.evilsocket.dsploit.gui.dialogs.ConfirmDialog.ConfirmDialogListener;
import it.evilsocket.dsploit.gui.dialogs.ErrorDialog;
import it.evilsocket.dsploit.gui.dialogs.FatalDialog;
import it.evilsocket.dsploit.gui.dialogs.InputDialog;
import it.evilsocket.dsploit.gui.dialogs.InputDialog.InputDialogListener;
import it.evilsocket.dsploit.gui.dialogs.MultipleChoiceDialog;
import it.evilsocket.dsploit.gui.dialogs.SpinnerDialog;
import it.evilsocket.dsploit.gui.dialogs.SpinnerDialog.SpinnerDialogListener;
import it.evilsocket.dsploit.net.Endpoint;
import it.evilsocket.dsploit.net.Network;
import it.evilsocket.dsploit.net.NetworkDiscovery;
import it.evilsocket.dsploit.net.Target;
import it.evilsocket.dsploit.net.metasploit.RPCServer;

import java.io.IOException;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.bugsense.trace.BugSenseHandler;

@SuppressLint("NewApi")
public class MainActivity extends SherlockListActivity {
	private String NO_WIFI_UPDATE_MESSAGE;
	private static final int WIFI_CONNECTION_REQUEST = 1012;
	private boolean isWifiAvailable = false;
	private TargetAdapter mTargetAdapter = null;
	private NetworkDiscovery mNetworkDiscovery = null;
	private EndpointReceiver mEndpointReceiver = null;
	private UpdateReceiver mUpdateReceiver = null;
	private RPCServer mRPCServer = null;
	private MsfrpcdReceiver mMsfRpcdReceiver = null;
	private Menu mMenu = null;
	private TextView mUpdateStatus = null;
	private Toast mToast = null;
	private long mLastBackPressTime = 0;
  private ActionMode mActionMode = null;

	private void createUpdateLayout() {

		getListView().setVisibility(View.GONE);
		findViewById(R.id.textView).setVisibility(View.GONE);

		RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout);
		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT);

		mUpdateStatus = new TextView(this);

		mUpdateStatus.setGravity(Gravity.CENTER);
		mUpdateStatus.setLayoutParams(params);
		mUpdateStatus
				.setText(NO_WIFI_UPDATE_MESSAGE.replace("#STATUS#", "..."));

		layout.addView(mUpdateStatus);

		if (mUpdateReceiver == null)
			mUpdateReceiver = new UpdateReceiver();

		if (mMsfRpcdReceiver == null)
			mMsfRpcdReceiver = new MsfrpcdReceiver();

		mUpdateReceiver.unregister();
		mMsfRpcdReceiver.unregister();

		mUpdateReceiver.register(MainActivity.this);

		startUpdateChecker();
		stopNetworkDiscovery(true);

		if (Build.VERSION.SDK_INT >= 11)
			invalidateOptionsMenu();
	}

	private void createOfflineLayout() {

		getListView().setVisibility(View.GONE);
		findViewById(R.id.textView).setVisibility(View.GONE);

		RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout);
		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT);

		mUpdateStatus = new TextView(this);

		mUpdateStatus.setGravity(Gravity.CENTER);
		mUpdateStatus.setLayoutParams(params);
		mUpdateStatus.setText(getString(R.string.no_connectivity));

		layout.addView(mUpdateStatus);

		stopNetworkDiscovery(true);
		if (Build.VERSION.SDK_INT >= 11)
			invalidateOptionsMenu();
	}

	public void createOnlineLayout() {
		mTargetAdapter = new TargetAdapter();

		setListAdapter(mTargetAdapter);

		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Target t = System.getTarget(position);
        if(t.getType() == Target.Type.NETWORK) {
          if(mActionMode==null)
            targetAliasPrompt(t);
          return true;
        }
        if(mActionMode==null) {
          mTargetAdapter.clearSelection();
          mActionMode = startActionMode(mActionModeCallback);
        }
        mTargetAdapter.toggleSelection(position);
        return true;
			}
		});

		if (mEndpointReceiver == null)
			mEndpointReceiver = new EndpointReceiver();

		if (mUpdateReceiver == null)
			mUpdateReceiver = new UpdateReceiver();

		if (mMsfRpcdReceiver == null)
			mMsfRpcdReceiver = new MsfrpcdReceiver();

		mEndpointReceiver.unregister();
		mUpdateReceiver.unregister();
		mMsfRpcdReceiver.unregister();

		mEndpointReceiver.register(MainActivity.this);
		mUpdateReceiver.register(MainActivity.this);
		mMsfRpcdReceiver.register(MainActivity.this);

		startUpdateChecker();
		startNetworkDiscovery(false);
		StartRPCServer();

		// if called for the second time after wifi connection
		if (Build.VERSION.SDK_INT >= 11)
			invalidateOptionsMenu();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (requestCode == WIFI_CONNECTION_REQUEST && resultCode == RESULT_OK
				&& intent.hasExtra(WifiScannerActivity.CONNECTED)) {
			System.reloadNetworkMapping();
			onCreate(null);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences themePrefs = getSharedPreferences("THEME", 0);
		Boolean isDark = themePrefs.getBoolean("isDark", false);
		if (isDark)
			setTheme(R.style.Sherlock___Theme);
		else
			setTheme(R.style.AppTheme);
		
	
		setContentView(R.layout.target_layout);
		NO_WIFI_UPDATE_MESSAGE = getString(R.string.no_wifi_available);
		isWifiAvailable = Network.isWifiConnected(this);
		boolean connectivityAvailable = isWifiAvailable
				|| Network.isConnectivityAvailable(this);
		

		// make sure system object was correctly initialized during application
		// startup
		if (!System.isInitialized()) {
			// wifi available but system failed to initialize, this is a fatal
			// :(
			if (isWifiAvailable) {
				new FatalDialog(getString(R.string.initialization_error),
						System.getLastError(), this).show();
				return;
			}
		}

		// initialization ok, but wifi is down
		if (!isWifiAvailable) {
			// just inform the user his wifi is down
			if (connectivityAvailable)
				createUpdateLayout();

			// no connectivity at all
			else
				createOfflineLayout();
		}
		// we are online, and the system was already initialized
		else if (mTargetAdapter != null)
			createOnlineLayout();
		// initialize the ui for the first time
		else {
			final ProgressDialog dialog = ProgressDialog.show(this, "",
					getString(R.string.initializing), true, false);

			// this is necessary to not block the user interface while
			// initializing
			new Thread(new Runnable() {
				@Override
				public void run() {
					dialog.show();

					Context appContext = MainActivity.this
							.getApplicationContext();
					String fatal = null;
					ToolsInstaller installer = new ToolsInstaller(appContext);

					if (!Shell.isRootGranted())
						fatal = getString(R.string.only_4_root);

					else if (!Shell.isBinaryAvailable("killall", true))
						fatal = getString(R.string.busybox_required);

					else if (!System.isARM())
						fatal = getString(R.string.arm_error)
								+ getString(R.string.arm_error2);

					else if (installer.needed() && !installer.install())
						fatal = getString(R.string.install_error);


					dialog.dismiss();

					if (fatal != null) {
						final String ffatal = fatal;
						MainActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								new FatalDialog(getString(R.string.error),
										ffatal, ffatal.contains(">"),
										MainActivity.this).show();
							}
						});
					}

					MainActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (!System.getAppVersionName().equals(
									System.getSettings().getString(
											"DSPLOIT_INSTALLED_VERSION", "0"))) {
								new ChangelogDialog(MainActivity.this).show();
								Editor editor = System.getSettings().edit();
								editor.putString("DSPLOIT_INSTALLED_VERSION",
										System.getAppVersionName());
								editor.commit();
							}
							try {
								createOnlineLayout();
							} catch (Exception e) {
								new FatalDialog(getString(R.string.error), e
										.getMessage(), MainActivity.this)
										.show();
							}
						}
					});
				}
			}).start();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main, menu);

		if (!isWifiAvailable) {
			menu.findItem(R.id.add).setVisible(false);
			menu.findItem(R.id.scan).setVisible(false);
			menu.findItem(R.id.new_session).setEnabled(false);
			menu.findItem(R.id.save_session).setEnabled(false);
			menu.findItem(R.id.restore_session).setEnabled(false);
			menu.findItem(R.id.settings).setEnabled(false);
			menu.findItem(R.id.ss_monitor).setEnabled(false);
			menu.findItem(R.id.ss_monitor).setEnabled(false);
			menu.findItem(R.id.ss_msfrpcd).setEnabled(false);
		}

		mMenu = menu;

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem item = menu.findItem(R.id.ss_monitor);

		if (mNetworkDiscovery != null && mNetworkDiscovery.isRunning())
			item.setTitle(getString(R.string.stop_monitor));
		else
			item.setTitle(getString(R.string.start_monitor));

		item = menu.findItem(R.id.ss_msfrpcd);

    if(!System.getSettings().getBoolean("MSF_ENABLED",true)) {
      item.setEnabled(false);
    } else if(!RPCServer.isLocal()) {
      item.setEnabled(true);
      if(System.getMsfRpc()==null)
        item.setTitle(getString(R.string.connect_msf));
      else
        item.setTitle(getString(R.string.disconnect_msf));
    } else if(RPCServer.exists() &&
            !System.isServiceRunning("it.evilsocket.dsploit.core.UpdateService")) {
      item.setEnabled(true);
      if (System.getMsfRpc() != null
          || (mRPCServer != null && mRPCServer.isRunning()))
        item.setTitle(getString(R.string.stop_msfrpcd));
      else
        item.setTitle(getString(R.string.start_msfrpcd));
    } else {
      item.setEnabled(false);
    }

		mMenu = menu;

		return super.onPrepareOptionsMenu(menu);
	}

  private void targetAliasPrompt(final Target target) {

    new InputDialog(getString(R.string.target_alias),
            getString(R.string.set_alias),
            target.hasAlias() ? target.getAlias() : "", true,
            false, MainActivity.this, new InputDialogListener() {
              @Override
              public void onInputEntered(String input) {
                target.setAlias(input);
                mTargetAdapter.notifyDataSetChanged();
              }
    }).show();
  }

  private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.main_multi, menu);
      return true;
    }

    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      int i = mTargetAdapter.getSelectedCount();
      mode.setTitle(i + " " + getString((i>1 ? R.string.targets_selected : R.string.target_selected)));
      MenuItem item = menu.findItem(R.id.multi_action);
      if(item!=null)
        item.setIcon((i>1 ? android.R.drawable.ic_dialog_dialer : android.R.drawable.ic_menu_edit));
      return false;
    }

    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      ArrayList<Plugin> commonPlugins = null;

      switch (item.getItemId()) {
        case R.id.multi_action:
          final int[] selected = mTargetAdapter.getSelectedPositions();
          if(selected.length>1) {
             commonPlugins = System.getPluginsForTarget(System.getTarget(selected[0]));
            for(int i =1; i <selected.length;i++) {
              ArrayList<Plugin> targetPlugins = System.getPluginsForTarget(System.getTarget(selected[i]));
              ArrayList<Plugin> removeThem = new ArrayList<Plugin>();
              for(Plugin p : commonPlugins) {
                if(!targetPlugins.contains(p))
                  removeThem.add(p);
              }
              for(Plugin p : removeThem) {
                commonPlugins.remove(p);
              }
            }
            if(commonPlugins.size()>0) {
              final int[] actions = new int[commonPlugins.size()];
              for(int i=0; i<actions.length; i++)
                actions[i] = commonPlugins.get(i).getName();

              (new MultipleChoiceDialog(R.string.choose_method,actions, MainActivity.this, new MultipleChoiceDialog.MultipleChoiceDialogListener() {
                @Override
                public void onChoice(int[] choices) {
                  Intent intent = new Intent(MainActivity.this,MultiAttackService.class);
                  int[] selectedActions = new int[choices.length];
                  int j=0;

                  for(int i =0; i< selectedActions.length;i++)
                    selectedActions[i] = actions[choices[i]];

                  intent.putExtra(MultiAttackService.MULTI_TARGETS, selected);
                  intent.putExtra(MultiAttackService.MULTI_ACTIONS, selectedActions);

                  startService(intent);
                }
              })).show();
            } else {
              (new ErrorDialog(getString(R.string.error),"no common actions found", MainActivity.this)).show();
            }
          } else {
            targetAliasPrompt(System.getTarget(selected[0]));
          }
          mode.finish(); // Action picked, so close the CAB
          return true;
        default:
          return false;
      }
    }

    // called when the user exits the action mode
    public void onDestroyActionMode(ActionMode mode) {
      mActionMode = null;
      mTargetAdapter.clearSelection();
    }
  };

	public void startUpdateChecker() {
		if (System.getSettings().getBoolean("PREF_CHECK_UPDATES", true)) {
			UpdateChecker mUpdateChecker = new UpdateChecker(this);
			mUpdateChecker.start();
		}
	}

	public void startNetworkDiscovery(boolean silent) {
		stopNetworkDiscovery(silent);

		mNetworkDiscovery = new NetworkDiscovery(this);
		mNetworkDiscovery.start();

		if (!silent)
			Toast.makeText(this, getString(R.string.net_discovery_started),
					Toast.LENGTH_SHORT).show();
	}

	public void stopNetworkDiscovery(boolean silent, boolean joinThreads) {
		if (mNetworkDiscovery != null) {
			if (mNetworkDiscovery.isRunning()) {
				mNetworkDiscovery.exit();

				if (joinThreads) {
					try {
						mNetworkDiscovery.join();
					} catch (Exception e) {
					}
				}

				if (!silent)
					Toast.makeText(this,
							getString(R.string.net_discovery_stopped),
							Toast.LENGTH_SHORT).show();
			}

			mNetworkDiscovery = null;
		}
	}

	public void stopNetworkDiscovery(boolean silent) {
		stopNetworkDiscovery(silent, true);
	}

  /**
   * start MSF RPC Daemon
   */
	public void StartRPCServer() {
    new Thread( new Runnable() {
      @Override
      public void run() {
        try {
          if (mRPCServer != null) {
            if(mRPCServer.isRunning()) {
              mRPCServer.exit();
              mRPCServer.join();
            }
            mRPCServer = null;
          }
          System.setMsfRpc(null);
        } catch ( Exception e) {
          // woop
        }
        // start MSF RPC Daemon only if it exists, user want it and no updates are in progress
        if((RPCServer.isInternal() || (RPCServer.isLocal() && RPCServer.exists())) && System.getSettings().getBoolean("MSF_ENABLED",true) && !System.isServiceRunning("it.evilsocket.dsploit.core.UpdateService")) {
          mRPCServer = new RPCServer(MainActivity.this);
          mRPCServer.start();
        }
      }
    }).start();
	}

  /**
   * stop MSF RPC Daemon
   * @param silent show an information Toast if {@code false}
   */
	public void StopRPCServer(final boolean silent) {
    new Thread( new Runnable() {
      @Override
      public void run() {
        Logger.debug("Stopping RPC Server");

        try {
          if (mRPCServer != null) {
            if(mRPCServer.isRunning()) {
              mRPCServer.exit();
              mRPCServer.join();
            }
            mRPCServer = null;
          }
          System.setMsfRpc(null);
          Shell.exec("killall msfrpcd");
          if(!silent)
            Toast.makeText(MainActivity.this, getString(R.string.rpcd_stopped),Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
          // woop
        }
      }
    }).start();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		
		case R.id.add:
			new InputDialog(getString(R.string.add_custom_target),
					getString(R.string.enter_url), MainActivity.this,
					new InputDialogListener() {
						@Override
						public void onInputEntered(String input) {
							final Target target = Target.getFromString(input);
							if (target != null) {
								// refresh the target listview
								MainActivity.this.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										if (System.addOrderedTarget(target)
												&& mTargetAdapter != null) {
											mTargetAdapter
													.notifyDataSetChanged();
										}
									}
								});
							} else
								new ErrorDialog(getString(R.string.error),
										getString(R.string.invalid_target),
										MainActivity.this).show();
						}
					}).show();
			return true;

		case R.id.scan:
			if (mMenu != null)
				mMenu.findItem(R.id.scan).setActionView(new ProgressBar(this));

			new Thread(new Runnable() {
				@Override
				public void run() {
					startNetworkDiscovery(true);

					MainActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (mMenu != null)
								mMenu.findItem(R.id.scan).setActionView(null);
						}
					});
				}
			}).start();

			item.setTitle(getString(R.string.stop_monitor));
			return true;

		case R.id.wifi_scan:
			stopNetworkDiscovery(true);

			if (mEndpointReceiver != null)
				mEndpointReceiver.unregister();

			if (mUpdateReceiver != null)
				mUpdateReceiver.unregister();

			startActivityForResult(new Intent(MainActivity.this,
					WifiScannerActivity.class), WIFI_CONNECTION_REQUEST);
			return true;

		case R.id.new_session:
			new ConfirmDialog(getString(R.string.warning),
					getString(R.string.warning_new_session), this,
					new ConfirmDialogListener() {
						@Override
						public void onConfirm() {
							try {
								System.reset();
								mTargetAdapter.notifyDataSetChanged();

								Toast.makeText(
										MainActivity.this,
										getString(R.string.new_session_started),
										Toast.LENGTH_SHORT).show();
							} catch (Exception e) {
								new FatalDialog(getString(R.string.error), e
										.toString(), MainActivity.this).show();
							}
						}

						@Override
						public void onCancel() {
						}

					}).show();

			return true;

		case R.id.save_session:
			new InputDialog(getString(R.string.save_session),
					getString(R.string.enter_session_name),
					System.getSessionName(), true, false, MainActivity.this,
					new InputDialogListener() {
						@Override
						public void onInputEntered(String input) {
							String name = input.trim().replace("/", "")
									.replace("..", "");

							if (!name.isEmpty()) {
								try {
									String filename = System.saveSession(name);

									Toast.makeText(
											MainActivity.this,
											getString(R.string.session_saved_to)
													+ filename + " .",
											Toast.LENGTH_SHORT).show();
								} catch (IOException e) {
									new ErrorDialog(getString(R.string.error),
											e.toString(), MainActivity.this)
											.show();
								}
							} else
								new ErrorDialog(getString(R.string.error),
										getString(R.string.invalid_session),
										MainActivity.this).show();
						}
					}).show();
			return true;

		case R.id.restore_session:
			final ArrayList<String> sessions = System
					.getAvailableSessionFiles();

			if (sessions != null && sessions.size() > 0) {
				new SpinnerDialog(getString(R.string.select_session),
						getString(R.string.select_session_file),
						sessions.toArray(new String[sessions.size()]),
						MainActivity.this, new SpinnerDialogListener() {
							@Override
							public void onItemSelected(int index) {
								String session = sessions.get(index);

								try {
									System.loadSession(session);
									mTargetAdapter.notifyDataSetChanged();
								} catch (Exception e) {
									e.printStackTrace();
									new ErrorDialog(getString(R.string.error),
											e.getMessage(), MainActivity.this)
											.show();
								}
							}
						}).show();
			} else
				new ErrorDialog(getString(R.string.error),
						getString(R.string.no_session_found), MainActivity.this)
						.show();
			return true;

		case R.id.settings:
			startActivity(new Intent(MainActivity.this, SettingsActivity.class));
			return true;

		case R.id.ss_monitor:
			if (mNetworkDiscovery != null && mNetworkDiscovery.isRunning()) {
				stopNetworkDiscovery(false);

				item.setTitle(getString(R.string.start_monitor));
			} else {
				startNetworkDiscovery(false);

				item.setTitle(getString(R.string.stop_monitor));
			}
			return true;

		case R.id.ss_msfrpcd:
      if(System.getMsfRpc()!=null || (mRPCServer!=null && mRPCServer.isRunning())) {
				StopRPCServer(false);
        if(RPCServer.isLocal())
				  item.setTitle(R.string.start_msfrpcd);
        else
          item.setTitle(R.string.connect_msf);
			} else {
				StartRPCServer();
        if(RPCServer.isLocal())
				  item.setTitle(R.string.stop_msfrpcd);
        else
          item.setTitle(R.string.disconnect_msf);
			}
			return true;

		case R.id.submit_issue:
			String uri = getString(R.string.github_issues);
			Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
			startActivity(browser);
			return true;

		case R.id.about:
			new AboutDialog(this).show();
			return true;

		default:
			return super.onOptionsItemSelected(item);

		}

	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

    if(mActionMode!=null) {
      ((TargetAdapter)getListAdapter()).toggleSelection(position);
      return;
    }

		new Thread(new Runnable() {
			@Override
			public void run() {
				/*
				 * Do not wait network discovery threads to exit since this
				 * would cause a long waiting when it's scanning big networks.
				 */
				stopNetworkDiscovery(true, false);

				startActivityForResult(new Intent(MainActivity.this,
						ActionActivity.class), WIFI_CONNECTION_REQUEST);

				overridePendingTransition(R.anim.slide_in_left,
						R.anim.slide_out_left);
			}
		}).start();

		System.setCurrentTarget(position);
		Toast.makeText(MainActivity.this,
				getString(R.string.selected_) + System.getCurrentTarget(),
				Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onBackPressed() {
		if (mLastBackPressTime < java.lang.System.currentTimeMillis() - 4000) {
			mToast = Toast.makeText(this, getString(R.string.press_back),
					Toast.LENGTH_SHORT);
			mToast.show();
			mLastBackPressTime = java.lang.System.currentTimeMillis();
		} else {
			if (mToast != null)
				mToast.cancel();

			new ConfirmDialog(getString(R.string.exit),
					getString(R.string.close_confirm), this,
					new ConfirmDialogListener() {
						@Override
						public void onConfirm() {
							MainActivity.this.finish();
						}

						@Override
						public void onCancel() {
						}
					}).show();

			mLastBackPressTime = 0;
		}
	}

	@Override
	public void onDestroy() {
		stopNetworkDiscovery(true);
		StopRPCServer(true);

		if (mEndpointReceiver != null)
			mEndpointReceiver.unregister();

		if (mUpdateReceiver != null)
			mUpdateReceiver.unregister();

		if (mMsfRpcdReceiver != null)
			mMsfRpcdReceiver.unregister();

		// make sure no zombie process is running before destroying the activity
		System.clean(true);

		BugSenseHandler.closeSession(getApplicationContext());

		super.onDestroy();
	}

	public class TargetAdapter extends ArrayAdapter<Target> {
		public TargetAdapter() {
			super(MainActivity.this, R.layout.target_list_item);
		}

		@Override
		public int getCount() {
			return System.getTargets().size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			TargetHolder holder;

			if (row == null) {
				LayoutInflater inflater = (LayoutInflater) MainActivity.this
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				row = inflater
						.inflate(R.layout.target_list_item, parent, false);

				holder = new TargetHolder();
				holder.itemImage = (ImageView) (row != null ? row
						.findViewById(R.id.itemIcon) : null);
				holder.itemTitle = (TextView) (row != null ? row
						.findViewById(R.id.itemTitle) : null);
				holder.itemDescription = (TextView) (row != null ? row
						.findViewById(R.id.itemDescription) : null);

				if (row != null)
					row.setTag(holder);
			} else
				holder = (TargetHolder) row.getTag();

			Target target = System.getTarget(position);

			if (target.hasAlias())
				holder.itemTitle.setText(Html.fromHtml("<b>"
						+ target.getAlias() + "</b> <small>( "
						+ target.getDisplayAddress() + " )</small>"));

			else
				holder.itemTitle.setText(target.toString());

      holder.itemTitle.setTextColor(getResources().getColor((target.isConnected() ? R.color.app_color : R.color.gray_text)));

      if(row!=null)
        row.setBackgroundColor(getResources().getColor((target.isSelected() ? R.color.abs__background_holo_dark : android.R.color.transparent)));

			holder.itemTitle.setTypeface(null, Typeface.NORMAL);
			holder.itemImage.setImageResource(target.getDrawableResourceId());
			holder.itemDescription.setText(target.getDescription());

			return row;
		}

    public void clearSelection() {
      for(Target t : System.getTargets())
        t.setSelected(false);
      notifyDataSetChanged();
      if(mActionMode!=null)
        mActionMode.finish();
    }

    public void toggleSelection(int position) {
      Target t = System.getTarget(position);
      t.setSelected(!t.isSelected());
      notifyDataSetChanged();
      if(mActionMode!=null) {
        if(getSelectedCount() > 0)
          mActionMode.invalidate();
        else
          mActionMode.finish();
      }
    }

    public int getSelectedCount() {
      int i = 0;
      for(Target t : System.getTargets())
        if(t.isSelected())
          i++;
      return i;
    }

    public ArrayList<Target> getSelected() {
      ArrayList<Target> result = new ArrayList<Target>();
      for(Target t : System.getTargets())
        if(t.isSelected())
          result.add(t);
      return result;
    }

    public int[] getSelectedPositions() {
      int[] res = new int[getSelectedCount()];
      int j=0;

      for(int i = 0; i < System.getTargets().size(); i++)
        if(System.getTarget(i).isSelected())
          res[j++]=i;
      return res;
    }

		class TargetHolder {
			ImageView itemImage;
			TextView itemTitle;
			TextView itemDescription;
		}
	}

	private class EndpointReceiver extends ManagedReceiver {
		private IntentFilter mFilter = null;

		public EndpointReceiver() {
			mFilter = new IntentFilter();

			mFilter.addAction(NEW_ENDPOINT);
			mFilter.addAction(ENDPOINT_UPDATE);
		}

		public IntentFilter getFilter() {
			return mFilter;
		}

		@SuppressWarnings("ConstantConditions")
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction() != null)
				if (intent.getAction().equals(NEW_ENDPOINT)) {
					String address = (String) intent.getExtras().get(
							ENDPOINT_ADDRESS), hardware = (String) intent
							.getExtras().get(ENDPOINT_HARDWARE), name = (String) intent
							.getExtras().get(ENDPOINT_NAME);
					final Target target = Target.getFromString(address);

					if (target != null && target.getEndpoint() != null) {
						if (name != null && !name.isEmpty())
							target.setAlias(name);

						target.getEndpoint().setHardware(
								Endpoint.parseMacAddress(hardware));

						// refresh the target listview
						MainActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								if (System.addOrderedTarget(target)) {
									mTargetAdapter.notifyDataSetChanged();
								}
							}
						});
					}
				} else if (intent.getAction().equals(ENDPOINT_UPDATE)) {
					// refresh the target listview
					MainActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mTargetAdapter.notifyDataSetChanged();
						}
					});
				}
		}
	}

	private class MsfrpcdReceiver extends ManagedReceiver {
		private IntentFilter mFilter = null;

		public MsfrpcdReceiver() {
			mFilter = new IntentFilter();

			mFilter.addAction(RPCServer.ERROR);
			mFilter.addAction(RPCServer.TOAST);
      mFilter.addAction(SettingsActivity.SETTINGS_WIPE_START);
      mFilter.addAction(SettingsActivity.SETTINGS_MSF_CHANGED);
		}

		public IntentFilter getFilter() {
			return mFilter;
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			/*
			 * optimization NOTES see?! no extra if(intent.getAction(...))
			 * required! just take the R.string.id to show from the intent
			 * extra, easy, fast and portable ;) we should use it for every
			 * ManagedReceiver, IMHO -- tux_mind
			 */
			final int message_id = intent.getIntExtra(RPCServer.STRINGID, R.string.error);

			if (intent.getAction().equals(RPCServer.ERROR)) {
				MainActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						new ErrorDialog(getString(R.string.error_rpc),
								getString(message_id), MainActivity.this).show();
					}
				});
			} else if(intent.getAction().equals(RPCServer.TOAST)){
				MainActivity.this.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            Toast.makeText(MainActivity.this,
                    getString(message_id), Toast.LENGTH_SHORT)
                    .show();
          }
        });
			} else if(intent.getAction().equals(SettingsActivity.SETTINGS_WIPE_START)) {
        try {
          StopRPCServer(true);
          Shell.exec("rm -rf '" + System.getGentooPath() + "'");
        } catch ( Exception e) {
          System.errorLogging(e);
        } finally {
          MainActivity.this.sendBroadcast(new Intent(SettingsActivity.SETTINGS_WIPE_DONE));
        }
      } else if(intent.getAction().equals(SettingsActivity.SETTINGS_MSF_CHANGED)){
        StopRPCServer(true);
        StartRPCServer();
      }
		}
	}

	private class UpdateReceiver extends ManagedReceiver {
		private IntentFilter mFilter = null;

		public UpdateReceiver() {
			mFilter = new IntentFilter();

			mFilter.addAction(UPDATE_CHECKING);
			mFilter.addAction(UPDATE_AVAILABLE);
			mFilter.addAction(UPDATE_NOT_AVAILABLE);
      mFilter.addAction(GENTOO_AVAILABLE);
      mFilter.addAction(UpdateService.ERROR);
      mFilter.addAction(UpdateService.DONE);
		}

		public IntentFilter getFilter() {
			return mFilter;
		}

		@SuppressWarnings("ConstantConditions")
		@Override
		public void onReceive(Context context, Intent intent) {
      if (mUpdateStatus != null
					&& intent.getAction().equals(UPDATE_CHECKING)
					&& mUpdateStatus != null) {
				mUpdateStatus.setText(NO_WIFI_UPDATE_MESSAGE.replace(
						"#STATUS#", getString(R.string.checking)));
			} else if (mUpdateStatus != null
					&& intent.getAction().equals(UPDATE_NOT_AVAILABLE)
					&& mUpdateStatus != null) {
				mUpdateStatus.setText(NO_WIFI_UPDATE_MESSAGE.replace(
						"#STATUS#", getString(R.string.no_updates_available)));
      } else if (intent.getAction().equals(GENTOO_AVAILABLE)) {
        if(mUpdateStatus != null)
          mUpdateStatus.setText(NO_WIFI_UPDATE_MESSAGE.replace(
            "#STATUS#", getString(R.string.new_msf_update_desc)));

        MainActivity.this.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            new ConfirmDialog(getString(R.string.update_available),
                    getString(R.string.new_msf_update_desc) + " " + getString(R.string.new_update_desc2),
                    MainActivity.this,
                    new ConfirmDialogListener() {
                      @Override
                      public void onConfirm() {
                        Intent i = new Intent(MainActivity.this,UpdateService.class);
                        i.setAction(UpdateService.START);
                        i.putExtra(UpdateService.ACTION, UpdateService.action.gentoo_update);
                        startService(i);
                      }

                      @Override
                      public void onCancel() {

                      }
                    }).show();
          }
        });
			} else if (intent.getAction().equals(UPDATE_AVAILABLE)) {
				final String remoteVersion = (String) intent.getExtras().get(
						AVAILABLE_VERSION);

				if (mUpdateStatus != null)
					mUpdateStatus.setText(NO_WIFI_UPDATE_MESSAGE.replace(
							"#STATUS#", getString(R.string.new_version)
									+ remoteVersion
									+ getString(R.string.new_version2)));

				MainActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						new ConfirmDialog(getString(R.string.update_available),
								getString(R.string.new_update_desc)
										+ remoteVersion
										+ getString(R.string.new_update_desc2),
								MainActivity.this, new ConfirmDialogListener() {
									@Override
									public void onConfirm() {
                    Intent i = new Intent(MainActivity.this,UpdateService.class);
                    i.setAction(UpdateService.START);
                    i.putExtra(UpdateService.ACTION, UpdateService.action.apk_update);
                    startService(i);
									}

									@Override
									public void onCancel() {
									}
								}).show();
					}
				});
			} else if(intent.getAction().equals(UpdateService.ERROR)) {
        final int messageId = intent.getIntExtra(UpdateService.MESSAGE, R.string.error_occured);
        MainActivity.this.runOnUiThread( new Runnable() {
          @Override
          public void run() {
            new ErrorDialog(
                    getString(R.string.error),
                    getString(messageId),
                    MainActivity.this)
                    .show();
          }
        });
      } else if(intent.getAction().equals(UpdateService.DONE)) {
        if(intent.getSerializableExtra(UpdateService.ACTION) == UpdateService.action.gentoo_update)
          StartRPCServer();
      }
		}
	}
}
