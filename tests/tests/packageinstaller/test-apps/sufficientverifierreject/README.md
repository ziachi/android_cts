To generate the publicKey below:

1. Generate RSA key
   `openssl genrsa -3 -out temp.pem 2048`

2. Create a certificate with the public part of the key
   `openssl req -new -x509 -key temp.pem -out sufficient_verifier_cert.x509.pem
   -days 10000`
   Fill in any additional details that the command may request

3. Create a PKCS#8-formatted version of the private key
   `openssl pkcs8 -in temp.pem -topk8 -outform DER -out
   sufficient_verifier_cert.pk8 -nocrypt`

4. Extract the public key from the key pair
   `openssl rsa -in temp.pem -outform PEM -pubout -out public_temp.pem`

5. Open public_temp.pem and copy the contents between the "BEGIN PUBLIC KEY"
   and "END PUBLIC KEY" tags. This should be the value of android:publicKey in
   <package-verifer>, in the AndroidManifest of the app defining a sufficient
   verifier.

6. Discard temp.pem and public_temp.pem
