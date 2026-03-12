/** @jest-environment jsdom */
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useMutation } from '@apollo/client';
import '@testing-library/jest-dom';
import UploadDialog from '@/components/ironbucket/UploadDialog';

jest.mock('@apollo/client', () => ({
  useMutation: jest.fn(),
  gql: (literals: TemplateStringsArray, ...placeholders: unknown[]) =>
    literals.reduce((acc, part, index) => acc + part + (placeholders[index] ?? ''), '')
}));

const mockedUseMutation = useMutation as jest.Mock;

describe('UploadDialog Component', () => {
  const mockBucket = 'test-bucket';
  let mockOnClose: jest.Mock;
  let mockOnSuccess: jest.Mock;
  let uploadMutation: jest.Mock;

  beforeEach(() => {
    mockOnClose = jest.fn();
    mockOnSuccess = jest.fn();
    uploadMutation = jest.fn().mockResolvedValue({
      data: {
        uploadObject: {
          key: 'test-file.txt',
          bucket: mockBucket,
          size: 1024
        }
      }
    });
    mockedUseMutation.mockReturnValue([uploadMutation]);
  });

  it('should render upload dialog', () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} />);

    expect(screen.getByText(/upload file/i)).toBeInTheDocument();
    expect(screen.getByText(/drag.*drop/i)).toBeInTheDocument();
  });

  it('should show bucket name in dialog', () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} />);

    expect(screen.getByText(new RegExp(mockBucket, 'i'))).toBeInTheDocument();
  });

  it('should allow file selection via file picker', async () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} />);

    const file = new File(['test content'], 'test-file.txt', { type: 'text/plain' });
    const input = screen.getByLabelText(/choose file/i);

    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByText('test-file.txt')).toBeInTheDocument();
    });
  });

  it('should support drag and drop', async () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} />);

    const dropzone = screen.getByText(/drag.*drop/i).closest('div');
    const file = new File(['test content'], 'test-file.txt', { type: 'text/plain' });

    fireEvent.drop(dropzone!, { dataTransfer: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByText('test-file.txt')).toBeInTheDocument();
    });
  });

  it('should validate file size limit', async () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} maxFileSize={1024} />);

    const largeFile = new File(['x'.repeat(2000)], 'large-file.txt', { type: 'text/plain' });
    const input = screen.getByLabelText(/choose file/i);

    fireEvent.change(input, { target: { files: [largeFile] } });

    await waitFor(() => {
      expect(screen.getByText(/file size exceeds limit/i)).toBeInTheDocument();
    });
  });

  it('should validate file type restrictions', async () => {
    render(
      <UploadDialog
        bucket={mockBucket}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
        allowedTypes={['text/plain', 'application/pdf']}
      />
    );

    const invalidFile = new File(['content'], 'script.js', { type: 'application/javascript' });
    const input = screen.getByLabelText(/choose file/i);

    fireEvent.change(input, { target: { files: [invalidFile] } });

    await waitFor(() => {
      expect(screen.getByText(/file type not allowed/i)).toBeInTheDocument();
    });
  });

  it('should show upload progress bar', async () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} />);

    const file = new File(['test content'], 'test-file.txt', { type: 'text/plain' });
    const input = screen.getByLabelText(/choose file/i);
    fireEvent.change(input, { target: { files: [file] } });

    const uploadButton = screen.getByRole('button', { name: /upload/i });
    fireEvent.click(uploadButton);

    await waitFor(() => {
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  it('should allow canceling upload', async () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} />);

    const file = new File(['test content'], 'test-file.txt', { type: 'text/plain' });
    const input = screen.getByLabelText(/choose file/i);
    fireEvent.change(input, { target: { files: [file] } });

    const uploadButton = screen.getByRole('button', { name: /upload/i });
    fireEvent.click(uploadButton);

    const cancelButton = await screen.findByText(/cancel/i);
    fireEvent.click(cancelButton);
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('should show error message on upload failure', async () => {
    uploadMutation.mockRejectedValueOnce(new Error('Upload failed'));
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} />);

    const file = new File(['test content'], 'test-file.txt', { type: 'text/plain' });
    const input = screen.getByLabelText(/choose file/i);
    fireEvent.change(input, { target: { files: [file] } });

    const uploadButton = screen.getByRole('button', { name: /upload/i });
    fireEvent.click(uploadButton);

    await waitFor(() => {
      expect(screen.getByText(/upload failed/i)).toBeInTheDocument();
    });
  });

  it('should call onSuccess callback after successful upload', async () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} />);

    const file = new File(['test content'], 'test-file.txt', { type: 'text/plain' });
    const input = screen.getByLabelText(/choose file/i);
    fireEvent.change(input, { target: { files: [file] } });

    const uploadButton = screen.getByRole('button', { name: /upload/i });
    fireEvent.click(uploadButton);

    await waitFor(() => {
      expect(uploadMutation).toHaveBeenCalledWith({
        variables: {
          bucket: mockBucket,
          key: 'test-file.txt',
          content: 'mock-file-content',
          contentType: 'text/plain'
        }
      });
    });

    await waitFor(() => {
      expect(mockOnSuccess).toHaveBeenCalledWith({
        key: 'test-file.txt',
        bucket: mockBucket,
        size: 1024
      });
    });
  });

  it('should close dialog after successful upload', async () => {
    render(<UploadDialog bucket={mockBucket} onClose={mockOnClose} onSuccess={mockOnSuccess} autoClose />);

    const file = new File(['test content'], 'test-file.txt', { type: 'text/plain' });
    const input = screen.getByLabelText(/choose file/i);
    fireEvent.change(input, { target: { files: [file] } });

    const uploadButton = screen.getByRole('button', { name: /upload/i });
    fireEvent.click(uploadButton);

    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled();
    });
  });
});
