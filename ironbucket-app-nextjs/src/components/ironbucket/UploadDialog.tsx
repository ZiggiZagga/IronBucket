'use client';

import { useMemo, useState } from 'react';
import { useMutation } from '@apollo/client';
import { UPLOAD_OBJECT } from '../../graphql/ironbucket-mutations';

type UploadResult = {
  key: string;
  bucket: string;
  size: number;
};

type UploadDialogProps = {
  bucket: string;
  onClose: () => void;
  onSuccess: (result: UploadResult) => void;
  maxFileSize?: number;
  allowedTypes?: string[];
  autoClose?: boolean;
};

export default function UploadDialog({
  bucket,
  onClose,
  onSuccess,
  maxFileSize,
  allowedTypes,
  autoClose
}: UploadDialogProps) {
  const [files, setFiles] = useState<File[]>([]);
  const [errorMessage, setErrorMessage] = useState('');
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);

  const [uploadObject] = useMutation(UPLOAD_OBJECT);

  const acceptedTypes = useMemo(() => allowedTypes ?? [], [allowedTypes]);

  const validateFiles = (nextFiles: File[]) => {
    if (maxFileSize && nextFiles.some((file) => file.size > maxFileSize)) {
      setErrorMessage('File size exceeds limit');
      return false;
    }

    if (acceptedTypes.length > 0 && nextFiles.some((file) => !acceptedTypes.includes(file.type))) {
      setErrorMessage('File type not allowed');
      return false;
    }

    setErrorMessage('');
    return true;
  };

  const setSelectedFiles = (nextFiles: File[]) => {
    if (!validateFiles(nextFiles)) {
      setFiles([]);
      return;
    }
    setFiles(nextFiles);
  };

  const onInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const nextFiles = Array.from(event.target.files ?? []);
    setSelectedFiles(nextFiles);
  };

  const onDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const nextFiles = Array.from(event.dataTransfer.files ?? []);
    setSelectedFiles(nextFiles);
  };

  const startUpload = async () => {
    if (files.length === 0) {
      return;
    }

    setUploading(true);
    setProgress(10);

    const file = files[0];
    try {
      const response = await uploadObject({
        variables: {
          bucket,
          key: file.name,
          content: 'mock-file-content',
          contentType: file.type || 'application/octet-stream'
        }
      });

      setProgress(100);
      const result = response.data?.uploadObject ?? {
        key: file.name,
        bucket,
        size: file.size
      };

      onSuccess(result);
      if (autoClose) {
        onClose();
      }
    } catch (error) {
      setErrorMessage('Upload failed');
    } finally {
      setUploading(false);
    }
  };

  return (
    <section>
      <h2>Upload File</h2>
      <p>Bucket: {bucket}</p>
      <div onDragOver={(event) => event.preventDefault()} onDrop={onDrop}>
        Drag and drop files here
      </div>

      <label htmlFor="upload-file-input">Choose file</label>
      <input id="upload-file-input" aria-label="Choose file" type="file" multiple onChange={onInputChange} />

      {files.map((file) => (
        <p key={file.name}>{file.name}</p>
      ))}

      {errorMessage && <p>{errorMessage}</p>}

      {uploading && (
        <div>
          <div role="progressbar" aria-valuenow={progress} />
          <button onClick={() => setUploading(false)}>Cancel</button>
        </div>
      )}

      <button onClick={startUpload}>Upload</button>
    </section>
  );
}
