package cn.way.appmanager.usage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import cn.way.appmanager.AppDownloadInfo;
import cn.way.appmanager.AppManager;
import cn.way.appmanager.DownloadService;
import cn.way.appmanager.DownloadService.DownloadBroadcastReceiver;
import cn.way.appmanager.DownloadService.DownloadServiceConnection;
import cn.way.appmanager.DownloadTask;
import cn.way.appmanager.DownloadTask.DownloadInfo;
import cn.way.appmanager.R;
import cn.way.wandroid.activityadapter.Piece;
import cn.way.wandroid.toast.Toaster;
import cn.way.wandroid.utils.WLog;

/**
 * @author Wayne
 * @2015年3月26日
 */
public class DownloadListFragment extends Piece<DownloadListPageAdapter> {
	private View view;
	private GridView gv;
	private ArrayList<AppDownloadInfo> items = new ArrayList<AppDownloadInfo>();
	private ArrayAdapter<AppDownloadInfo> adapter ;
	private void onClickStateButton(View v,int position){
		DownloadInfo dinfo = items.get(position).getDownloadInfo();
		//如果已经安装并且不需要更新，执行打开操作，否则执行下载操作
		if (items.get(position).isInstalled(getActivity())&&!items.get(position).isNeedUpdate(getActivity())) {
			AppManager.openApp(getActivity(), items.get(position).getPackageName());
		}else{
			if (dinfo.getProgress()==100) {
				AppManager.installApp(getActivity(), dinfo.getFile());
			}else{
				if (downloadService!=null&&!dinfo.isEmpty()) {
					DownloadTask dt = downloadService.createDownloadTask(items.get(position), null);
					if (dt!=null) {
						dt.start(getActivity());
						v.setVisibility(View.INVISIBLE);
						Toast.makeText(getActivity(), dinfo.toString(), 0).show();
					}
				}
			}
		}
	}
	private void updateView(){
		adapter.notifyDataSetChanged();
	}
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		adapter = new ArrayAdapter<AppDownloadInfo>(getActivity(), 0,items){
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = convertView;
				ViewHolder vh = null;
				if (view==null) {
					view = getActivity().getLayoutInflater().inflate(R.layout.appmanager_download_list_item, null);
					if (view != null) {
						final ViewHolder holder = new ViewHolder();
						view.setTag(holder);
						holder.pb = (ProgressBar) view.findViewById(R.id.progressBar);
						holder.btn = (Button) view.findViewById(R.id.button);
						holder.stateTv = (TextView) view.findViewById(R.id.stateTv);
						holder.btn.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								onClickStateButton(v,holder.position);
							}
						});
						vh = holder;
					}
				}else{
					vh = (ViewHolder) view.getTag();
				}
				if(vh!=null){
					vh.position = position;
					AppDownloadInfo info = getItem(position);
					int progress = info.getDownloadInfo().getProgress();
					DownloadInfo dif = info.getDownloadInfo();
					if (downloadService!=null) {
						vh.pb.setProgress(progress);
						DownloadTask dt = downloadService.getDownloadTask(info.getDownloadInfo().getUrl());
						if (dt!=null&&dt.isRunning()) {
							vh.btn.setVisibility(View.INVISIBLE);
							int bytesWritten = dif.getBytesWritten(); 
							int totalSize = dif.getTotalSize();
							int bytesPerSec = dif.getBytesPerSec(); 
							int duration = dif.getDuration();
							int s = duration%60;
							int m = duration/60%60;
							int h = duration/60/60%60;
							float speed = bytesPerSec;
							if (speed>=1024*1000) {//>=1024*1000B/s
								speed = bytesPerSec/1024.0f/1024.0f;
								vh.stateTv.setText(String.format("%d/%d   %d%%    %.1fM/s   %02d:%02d:%02d",bytesWritten,totalSize,progress, speed,h,m,s));
							}else if(speed>=1000&&speed <1000*1024){//<1000B/s
								speed = bytesPerSec/1024.0f;
								vh.stateTv.setText(String.format("%d/%d   %d%%    %.0fK/s   %02d:%02d:%02d",bytesWritten,totalSize,progress, speed,h,m,s));
							}else if(speed<1000){
								vh.stateTv.setText(String.format("%d/%d   %d%%    %.0fB/s   %02d:%02d:%02d",bytesWritten,totalSize,progress, speed,h ,m,s));
							}
						}else{
							vh.stateTv.setText("");
							vh.btn.setVisibility(View.VISIBLE);
							if (info.isInstalled(getActivity())) {
								if (info.isNeedUpdate(getContext())) {
									vh.btn.setText("更新");
								}else{
									vh.btn.setText("打开");
								}
							}else{
								if (progress==100) {
									vh.btn.setText("安装");
								}else{
									if (progress>0) {
										vh.btn.setText("继续");
									}else{
										vh.btn.setText("下载");
									}
								}
							}
						}
					}
				}
				return view;
			}
			class ViewHolder {
				int position;
				ProgressBar pb;
				Button btn;
				TextView stateTv;
			}
		};
		
//		data = new HashMap<String, AppDownloadInfo>();
//		AppDownloadInfo info = new AppDownloadInfo();
//		DownloadInfo dinfo = new DownloadInfo();
//		info.setDownloadInfo(dinfo);
//		info.setPackageName("cn.way.wandroid");
//		data.put("abc", info);
//		persistDownloadInfos();
//		loadDownloadInofs();
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (view==null) {
			view = inflater.inflate(R.layout.appmanager_download_list, container, false);
			setupView();
		}else{
			((ViewGroup)view.getParent()).removeView(view);
		}
		return view;
	}
	private void setupView(){
		if (view==null) {
			return;
		}
		gv = (GridView) view.findViewById(R.id.gridView);
		gv.setAdapter(adapter);
		loadApps();
	}
	private void loadApps(){
		HashMap<String, AppDownloadInfo> data = DownloadService.readDownloadInofs(getActivity());
		//做假数据
		for (int i = 0; i < 10; i++) {
			AppDownloadInfo info = null;
			String url = "http://gdown.baidu.com/data/wisegame/6d1bab87db9d5a30/weixin_542.apk?i="+i;
			String packageName = "cn.way.wandroid"+i;
			int versionCode = i;
			if (i==2) {
				packageName = "com.yaoji.yaoprize";
				versionCode = 15;
			}
			if (data.containsKey(packageName)) {
				info = data.get(packageName);
				info.setVersionCode(versionCode);
				info.setInstalled(false);//重新设置状态，因为这个状态每次都根据本地数据来更新
				if (!info.getDownloadInfo().getFile().exists()) {//如果文件不存在则重置下载进度
					info.getDownloadInfo().reset();
				}
				if (info.isNeedUpdate(getActivity())) {
					info.getDownloadInfo().reset();
				}
				
			}else{
				info = new AppDownloadInfo();
				DownloadInfo dinfo = new DownloadInfo(url,createFile(url));
				info.setDownloadInfo(dinfo);
				info.setPackageName(packageName);
				info.setVersionCode(versionCode);
			}
			
			items.add(info);
		}
	}
	private File createFile(String path){
		return new File(getActivity().getExternalCacheDir(), path.charAt(path.length()-1)+".apk");
	}
	
	private void toast(String text){
		Toaster.instance(getActivity()).setup(text).show();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		DownloadService.bind(getActivity(), serviceConnection);
		DownloadService.registerReceiver(getActivity(), receiver);
		updateView();
	}
	@Override
	public void onPause() {
		super.onPause();
		DownloadService.unbind(getActivity(), serviceConnection);
		DownloadService.unregisterReceiver(getActivity(), receiver);
	}
	@Override
	public void onPageReady() {

	}

	DownloadBroadcastReceiver receiver = new DownloadBroadcastReceiver(){
		@Override
		public void onUpdate() {
			WLog.d("update");
			updateView();
		}
	};
	DownloadService downloadService;
	DownloadServiceConnection serviceConnection = new DownloadServiceConnection() {
		@Override
		public void onServiceDisconnected(DownloadService service) {
			serviceConnection = null;
		}
		@Override
		public void onServiceConnected(DownloadService service) {
			downloadService = service;
			updateView();
		}
	};
}