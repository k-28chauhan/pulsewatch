import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';
import { mockApi, problem, renderApp } from '../test/utils';

const payload = btoa(JSON.stringify({ sub: '1', email: 'ann@example.com' }));
const token = `h.${payload}.s`;

function renderLogin() {
  return renderApp(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<p>Signed in home</p>} />
    </Routes>,
    '/login',
  );
}

describe('LoginPage', () => {
  it('signs in, stores the session and leaves for the home page', async () => {
    const { calls } = mockApi({ 'POST /api/auth/login': { token, tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600_000).toISOString() } });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Email'), 'ann@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Signed in home')).toBeInTheDocument();
    expect(calls[0]).toMatchObject({ method: 'POST', url: '/api/auth/login', body: { email: 'ann@example.com', password: 'correct horse' } });
    expect(JSON.parse(localStorage.getItem('pulsewatch.session')!)).toMatchObject({ token, email: 'ann@example.com' });
  });

  it('shows the server message for a wrong password and stays on the page', async () => {
    mockApi({ 'POST /api/auth/login': problem(401, 'Invalid email or password') });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Email'), 'ann@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(localStorage.getItem('pulsewatch.session')).toBeNull();
  });

  it('keeps the submit button disabled until both fields are filled', async () => {
    mockApi({});
    renderLogin();

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Email'), 'ann@example.com');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Password'), 'x');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
  });

  it('switches to registration and checks the password length before calling the server', async () => {
    const { fetchMock } = mockApi({});
    renderLogin();

    await userEvent.click(screen.getByRole('button', { name: 'Create an account' }));
    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Email'), 'new@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Use at least 8 characters')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows field errors from the server on registration (a taken email)', async () => {
    mockApi({ 'POST /api/auth/register': problem(409, 'An account with this email already exists') });
    renderLogin();

    await userEvent.click(screen.getByRole('button', { name: 'Create an account' }));
    await userEvent.type(screen.getByLabelText('Email'), 'ann@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('already exists'));
  });

  it('sends a signed-in visitor straight to the app', () => {
    localStorage.setItem('pulsewatch.session', JSON.stringify({ token, expiresAt: new Date(Date.now() + 3600_000).toISOString(), email: 'ann@example.com' }));
    mockApi({});
    renderLogin();

    expect(screen.getByText('Signed in home')).toBeInTheDocument();
    vi.unstubAllGlobals();
  });
});
