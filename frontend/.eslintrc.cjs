module.exports = {
  root: true,
  env: {
    browser: true,
    es2021: true,
    node: true,
  },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react/recommended',
    'plugin:react-hooks/recommended',
  ],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaFeatures: {
      jsx: true,
    },
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  plugins: ['react', '@typescript-eslint', 'react-hooks'],
  settings: {
    react: {
      version: 'detect',
    },
  },
  rules: {
    // React 17+ doesn't require React in scope for JSX
    'react/react-in-jsx-scope': 'off',
    // Downgrade unused vars to warning for now (can tighten later)
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    // Allow explicit any (can tighten later)
    '@typescript-eslint/no-explicit-any': 'warn',
    // Hooks rules
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    // Downgrade ts-ignore/ts-expect-error to warning (legacy code)
    '@typescript-eslint/ban-ts-comment': 'warn',
    // Allow empty blocks (sometimes used intentionally)
    'no-empty': 'warn',
    // Allow arguments object (legacy code)
    'prefer-rest-params': 'warn',
  },
  ignorePatterns: ['dist', 'node_modules', '*.config.js', '*.config.ts', 'vite-env.d.ts'],
};
