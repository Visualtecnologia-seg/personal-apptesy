import Resizer from "react-image-file-resizer";

export const getTypeFile = (file) => {
  return file.name.substring(file.name.lastIndexOf(".") + 1);
}

export const getFileName = (file) => {
  return file.name.substring(0, file.name.lastIndexOf(".") - 1);
}

export const resizeFile = async (file) => {
  const quality = file.size < 500000 ? 85 : 75;
  return new Promise((resolve) => {
    Resizer.imageFileResizer(
        file,
        1440,
        2560,
        "JPEG",
        quality,
        0,
        (uri) => {
          resolve(uri);
        },
        "file",
    );
  });
};