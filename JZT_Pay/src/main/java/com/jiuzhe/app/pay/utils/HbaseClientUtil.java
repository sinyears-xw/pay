package com.jiuzhe.app.pay.utils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.LocalHBaseCluster;
import com.jiuzhe.app.pay.utils.NetUtil;

import java.io.*;

public class HbaseClientUtil {

    private static Configuration conf = null;
    
    static {

        conf = HBaseConfiguration.create();  
        conf.set("hbase.zookeeper.quorum", "127.0.0.1");
        conf.set("hbase.zookeeper.property.clientPort", "2181");
        conf.setInt("hbase.rpc.timeout", 1000); 
        conf.setInt("hbase.client.retries.number", 1);
        conf.setInt("hbase.client.operation.timeout", 2000);
        conf.setInt("hbase.client.pause", 20);
    }

    public static boolean addDataToHbase(String rowKey, String tableName, String data) throws Exception {
        
        Table table = null;
        Connection connection = null;
        if (!NetUtil.isLoclePortUsing(2181))
          return false;
        try {           
            connection = ConnectionFactory.createConnection(conf);
            table = connection.getTable(TableName.valueOf(tableName));
            Put put = new Put(Bytes.toBytes(rowKey));// 设置rowkey  
    		put.add(Bytes.toBytes("data"), Bytes.toBytes(""), Bytes.toBytes(data));
            table.put(put);
            return true;
        } catch(Exception e) {
            return false;
        } finally {
            if (table != null)
                table.close();
            if (connection != null)
                connection.close();
        }
        
    }

    // public static boolean addDataToMysql(String rowKey, String tableName, String data) throws IOException {
    //     Connection connection = null;
    //     Table table = null;
    //     try {
    //         connection = ConnectionFactory.createConnection(conf);
    //         table = connection.getTable(TableName.valueOf(tableName));
    //         Put put = new Put(Bytes.toBytes(rowKey));// 设置rowkey  
    //         put.add(Bytes.toBytes("data"), Bytes.toBytes(""), Bytes.toBytes(data));
    //         table.put(put);
    //         return true;
    //     } catch(Exception e) {
    //         return false;
    //     } finally {
    //         if (connection != null)
    //             connection.close();
    //         if (table != null)
    //             table.close();
    //     }
        
    // }
}
