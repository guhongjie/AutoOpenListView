package ghj.widgit.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import guhj.github.widget.AutoOpenListView;

public class TestActivity extends Activity {


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        AutoOpenListView listView = (AutoOpenListView) findViewById(R.id.mylistview);
        listView.setAdapter(new Adapter());
    }

    class Adapter extends BaseAdapter implements AutoOpenListView.OpenViewAdapter {
        @Override
        public View getOpenView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(getOpenItemViewType(position) == 1 ? R.layout.activity_test_item_open_2 : R.layout.activity_test_item_open, parent, false);
                Log.d("OpenListView", String.format("create OpenView:positoin=%d", position));
            }
            ((TextView)convertView.findViewById(R.id.tv_content)).setText("" + position);
            return convertView;
        }

        @Override
        public int getCount() {
            return 10000;
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.activity_test_item, parent, false);
                Log.d("OpenListView", String.format("create View:positoin=%d", position));
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getBaseContext(), TestActivity.class);
                        startActivity(intent);
                    }
                });
            }
            return convertView;
        }

        @Override
        public int getOpenItemViewType(int position) {
            return position % 3 == 0 ? 1 : 2;
        }

        @Override
        public void openViewClip(int position, View itemView, float clip, int clipMode) {

        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

    }
}
