/** @jest-environment node */
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';

describe('e2e gateway client', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('fetches actor token with password grant', async () => {
    const fetchMock = jest.spyOn(global, 'fetch' as never).mockResolvedValue({
      ok: true,
      json: async () => ({ access_token: 'token-123' })
    } as Response);

    const token = await fetchActorAccessToken('alice');

    expect(token).toBe('token-123');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, options] = fetchMock.mock.calls[0];
    expect(options?.method).toBe('POST');
  });

  it('sends graphql request with actor and trace headers', async () => {
    const fetchMock = jest.spyOn(global, 'fetch' as never).mockResolvedValue({
      ok: true,
      json: async () => ({ data: { ok: true } })
    } as Response);

    const response = await callGatewayGraphql('jwt-a', {
      query: 'query Q { ok }',
      variables: { a: 1 }
    }, {
      actor: 'alice',
      traceparent: '00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01'
    });

    expect(response.data.ok).toBe(true);
    const [, options] = fetchMock.mock.calls[0];
    expect(options?.headers).toMatchObject({
      Authorization: 'Bearer jwt-a',
      traceparent: '00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01',
      'x-ironbucket-actor': 'alice'
    });
  });

  it('throws when graphql returns errors', async () => {
    jest.spyOn(global, 'fetch' as never).mockResolvedValue({
      ok: true,
      json: async () => ({ errors: [{ message: 'boom' }] })
    } as Response);

    await expect(
      callGatewayGraphql('jwt-a', {
        query: 'query Q { ok }'
      })
    ).rejects.toThrow(/GraphQL errors/i);
  });
});
