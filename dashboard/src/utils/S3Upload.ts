import {baseURL} from "../service/Api";
import {s3UploadService} from "../service/S3UploadService";

const token = JSON.parse(localStorage.getItem("@blueshark-personal-token") as string);

class S3Upload {
  loader: any;
  url: string;
  xhr: any;

  constructor(props) {
    // CKEditor 5's FileLoader instance.
    this.loader = props;
    // URL where to send files.
    this.url = `${baseURL}/s3/upload-image/`;

  }

  // Starts the upload process.
  upload() {
    return new Promise((resolve, reject) => {
      this._initRequest();
      this._initListeners(resolve, reject);
      this._sendRequest(resolve);
    });
  }

  // Aborts the upload process.
  abort() {
    if (this.xhr) {
      this.xhr.abort();
    }
  }

  // Example implementation using XMLHttpRequest.
  _initRequest() {
    const xhr = this.xhr = new XMLHttpRequest();

    xhr.open("POST", this.url, true);
    xhr.responseType = "json";
    xhr.setRequestHeader("Access-Control-Allow-Origin", "*");
    xhr.setRequestHeader("Authorization", "Bearer " + token);
  }

  // Initializes XMLHttpRequest listeners.
  _initListeners(resolve, reject) {
    const xhr = this.xhr;
    const loader = this.loader;
    // const genericErrorText = 'Couldn\'t upload file:' + ` ${ loader.file.name }.`;

    // xhr.addEventListener( 'error', () => reject( genericErrorText ) );
    xhr.addEventListener("abort", () => reject());
    xhr.addEventListener("load", () => {
      const response = xhr.response;
      if (!response || response.error) {
        // return reject( response && response.error ? response.error.message : genericErrorText );
      }

      // If the upload is successful, resolve the upload promise with an object containing
      // at least the "default" URL, pointing to the image on the server.
      resolve({
        default: response,
      });
    });

    if (xhr.upload) {
      xhr.upload.addEventListener("progress", evt => {
        if (evt.lengthComputable) {
          loader.uploadTotal = evt.total;
          loader.uploaded = evt.loaded;
        }
      });
    }
  }

  // Prepares the data and sends the request.
  _sendRequest(resolve) {
    const data = new FormData();
    this.loader.file.then(file => {
        data.append("file", file);
        s3UploadService(data).then(res => {
          resolve({
            default: res.data,
          });
        });
        // TODO Verificar o código
        // this.xhr.send(data);
      },
    );
  }

}

export default S3Upload;
