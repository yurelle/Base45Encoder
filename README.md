# Base45Encoder
Standalone Java implementation of the RFC-9285 Base45 Standard. It implements all of the tests & security conditions mentioned in the standard, as well as a few more.


## License
This code is released under the Unlicence. In short, you can do whatever you want with it, with no consequences. This code is considered part of the public domain. You can modify it, redistribute it, and/or use it in personal, open-source, & commercial projects freely.

## Security
Only version 2.1.0 or later should be used. The version 2.0.0 rewrite to match the RFC-9285 Base45 Standard did not implement the security restrictions recommended in the standard. These were implemented in v2.1.0 the next day. The even older version 1.0.0 was not compliant with the standard at all, and was only intended as a specialized domain specific utility package. Version 1 is obsolete, and should not be used at all. Version 2.0 matches the standard's core functionality, but does not have the security checks, and is therefore not recommended for serious projects. It is recommended that you only use v2.1.0 or later.

# How To Use
Just import the Jar file into the classpath, and call the following wrapper functions. I have not pushed this code to maven central yet. I started looking into it, but I'm not sure when I'll have the time.

## Byte Arrays
    //Encode
    final byte[] myBinaryData = ... ;
    final String encodedStr = Base45.encode(myBinaryData);
    
    //Decode
    final byte[] decodedBytes = Base45.decode(encodedStr);

## Input Streams
    //Encode
    final InputStream in_1 = ... ;
    final String encodedStr = Base45.encode(in_1);
    
    //Decode
    final InputStream in_2 = ... ;
    final byte[] decodedBytes = Base45.decode(in_2);
