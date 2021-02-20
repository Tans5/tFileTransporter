
## Summary

Use TCP protocol to transfer infomation. This program builds with RxJava and Kotlin Coroutines.

Apks:  
[v1.0.1](https://github.com/Tans5/tFileTransporter/blob/master/apks/tFileTransfer_v1.0.1.apk)

## Screenshots

<img src="screenshots/screenshot_1.png" height="40%" width="40%"/>  <img src="screenshots/screenshot_2.png" height="40%" width="40%"/> 

<img src="screenshots/screenshot_3.png" height="40%" width="40%"/>  <img src="screenshots/screenshot_4.png" height="40%" width="40%"/> 

<img src="screenshots/screenshot_5.png" height="40%" width="40%"/>  <img src="screenshots/screenshot_6.png" height="40%" width="40%"/> 

<img src="screenshots/screenshot_8.png" height="40%" width="40%"/>  <img src="screenshots/screenshot_9.png" height="40%" width="40%"/>

<img src="screenshots/screenshot_7.png" height="40%" width="40%"/>


## Network

All text use UTF-8 encode.

### Broadcast

#### BroadcastSender 

Broadcast Server will send UDP broadcast message (Example: Google Pixel XL) in endless loop. 

1. Broadcast Receiver Port: `6666`

#### BroadcastListener

Broadcast Sender will create a TCP listener to accecpt client's connection request.

1. Broadcast Listener Port: `6667`

2. Data Transfer

- Read: The size of client device info (4 bytes)

- Read: The client device info (Size Of Device Info bytes)

- Write: If accept client's connection request (1 byte)
 
	- Accept: `0x00`
	- Deny: `0x01`

 
### Information Transfer

Below introduction omits write Action process and client connecting process. TCP Server and TCP Client all need handle write and read process. The Server's max connection is 1.

#### Server Listen and Exchange Info with Client
- Server Listen Port: `6668`
- Write: VERSION (1 byte, Now latest version is 0x01, if client has a diffrent version and would close connection)
- Read: Client Files' Separator Size (4 bytes)
- Read: Client Files' Separator (Separator Size bytes, Windows is `\` and Android is `/`)
- Write: Server Files' Separator Size (4 bytes)
- Write: Server Files' Separator (Separator Size bytes)


#### Read Actions

Each Actions first byte is Action Code.

##### Request Folder Children Action

Remote device request folder's chidren.

- Action Code: `0x00`
- Data Read
	- folder size (4 bytes)
	- folder (folder size bytes, type: text)  
   Example: `/home/user/downloads`
   
##### Folder Children Share Action

Get remote device folder's children.

- Action Code: `0x01`
- Data Read
  - children's data size (4 bytes)
  - children's data (data size bytes, type: json)   
    Example:
     
    ```json

		{
    		"path":"/home/user/downloads",
    		"children_folders":[
        		{
            		"name":"test_folder",
            		"path":"/home/user/downloads/test_folder",
            		"child_count":100,
            		"last_modify":"2021-01-01T00:00:00+08:00"
        		}
    		],
    		"children_files":[
        		{
            		"name":"test_file",
            		"path":"/home/user/downloads/test_file",
            		"size":10000000,
            		"last_modify":"2021-01-01T00:00:00+08:00"
        		}
    		]
		}
    
    ```

##### Request Files Share Action

Remote device request download files.

- Action Code: `0x02`
- Data Read
  - files' data size (4 bytes)
  - files' data (data size: bytes, type: json)   
    Example: 
    
    ```json
      
      [{
      	"md5": [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      	"file": {
            	"name":"test_file",
            	"path":"/home/user/downloads/test_file",
            	"size":10000000,
            	"last_modify":"2021-01-01T00:00:00+08:00"
        	}
      }]
    
    ```
    
##### Files Share Action

Download remote devices' files.

- Action Code: `0x03`  
- Data Read
  - files' data size (4 bytes)
  - files' data (data size: bytes, type: json)   
    Example: 
    
    ```json
      
      [{
      	"md5": [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      	"file": {
            	"name":"test_file",
            	"path":"/home/user/downloads/test_file",
            	"size":10000000,
            	"last_modify":"2021-01-01T00:00:00+08:00"
        	}
      }]
    
    ```
    
And then download files, see `Files Transfer`.

##### Send Message Action

Receive remote devices' message.

- Action Code: `0x04`
- Data Read
  - message size (4 bytes)
  - message (message size bytes, type: text)   
    Example: `Hello, World.`

### Files Transfer

Use TCP protocol to transfer files. Servers send files and clients downloads files. A TCP connection transfer a file's frame. See `Files Share Action`.  
Below introduction omits client deal process.

- Server Listener Port: `6669`
- Server Max Connection: 30
- Send File's Frame Data  
  - Read: File's MD5 (16 bytes)  
  - Read: File's Frame Start (8 bytes)
  - Read: File's Frame End (8 bytes)
  - Write: File's Frame data (`Frame End` - `Frame Start` bytes)

